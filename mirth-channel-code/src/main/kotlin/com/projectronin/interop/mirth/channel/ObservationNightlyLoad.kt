package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.fhir.ronin.resource.RoninObservations
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.destinations.ObservationWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.ServiceFactoryImpl
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException

private const val PUBLISH_SERVICE = "publish"
private val categoryValueSet = CodeSystem.OBSERVATION_CATEGORY.uri.value

class ObservationNightlyLoad(serviceFactory: ServiceFactory = ServiceFactoryImpl) : ChannelService(serviceFactory) {
    companion object : ChannelFactory<ObservationNightlyLoad>()

    override val rootName = "ObservationLoad"
    override val destinations = mapOf(PUBLISH_SERVICE to ObservationWriter(rootName, serviceFactory))

    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        // Query the Ronin clinical data store: get all Patient FHIR IDs for this tenant
        val patientService = serviceFactory.patientService()
        val patientList = patientService.getPatientFHIRIdsByTenant(tenantMnemonic)
        if (patientList.isEmpty()) {
            throw ResourcesNotFoundException("No Patients found in clinical data store for tenant $tenantMnemonic")
        }

        // Query the tenant EHR system: get all FHIR Observations for these patients in these category codes
        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val vendorFactory = serviceFactory.vendorFactory(tenant)

        // Query the tenant EHR system 1 patient at a time. Collect results and send to Mirth
        val mirthMessageList = patientList.flatMap { fhirId ->
            val patientFhirId = fhirId.unlocalize(tenant)
            val response = vendorFactory.observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf(
                    patientFhirId
                ),
                listOf(
                    FHIRSearchToken(categoryValueSet, ObservationCategoryCodes.VITAL_SIGNS.code),
                    FHIRSearchToken(categoryValueSet, ObservationCategoryCodes.LABORATORY.code)
                )
            )
            response.chunked(confirmMaxChunkSize(serviceMap)).map { obsList ->
                MirthMessage(
                    message = JacksonUtil.writeJsonValue(obsList),
                    dataMap = mapOf(
                        MirthKey.PATIENT_FHIR_ID.code to patientFhirId,
                        MirthKey.RESOURCES_FOUND.code to obsList,
                        MirthKey.RESOURCE_TYPE.code to obsList.first().resourceType,
                        MirthKey.RESOURCE_COUNT.code to obsList.count()
                    )
                )
            }
        }
        return mirthMessageList
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val observations = JacksonUtil.readJsonList(msg, Observation::class)
        if (observations.isEmpty()) {
            throw ResourcesNotFoundException("No Observations found for tenant $tenantMnemonic")
        }

        val observationsTransformed = transformToList(tenantMnemonic, observations, RoninObservations)
        if (observationsTransformed.isEmpty()) {
            throw ResourcesNotTransformedException("Failed to transform Observations for tenant $tenantMnemonic")
        }

        val observationFHIRIds = observationsTransformed.mapNotNull { resource -> resource.id?.value }
        return MirthMessage(
            message = JacksonUtil.writeJsonValue(observationsTransformed),
            dataMap = mapOf(
                MirthKey.RESOURCES_TRANSFORMED.code to observationsTransformed,
                MirthKey.FHIR_ID_LIST.code to observationFHIRIds.joinToString(",")
            )
        )
    }
}
