package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.connector.ServiceFactory

class AppointmentByPractitionerPatientWriter(rootName: String, serviceFactory: ServiceFactory) :
    DestinationService(rootName, serviceFactory) {
    /**
     * determines if we need to resolve a patient reference
     * Looks into the Ronin clinical data store for a given patient and returns the FHIR ID if found
     */
    override fun channelDestinationFilter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        // push patient to the Ronin clinical data store if need be
        return MirthFilterResponse(sourceMap.containsKey(MirthKey.NEW_PATIENT_JSON.code))
    }

    /**
     * resolves an unloaded patient reference. Retrieves the patient from the vendor, transforms and publishes it
     */
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val patient = JacksonUtil.readJsonObject(sourceMap[MirthKey.NEW_PATIENT_JSON.code] as String, Patient::class)
        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val vendorFactory = serviceFactory.vendorFactory(tenant)

        val roninPatient = RoninPatient.create(vendorFactory.identifierService)
        val transformedPatient = patient.transformTo(roninPatient, tenant)
            ?: return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = JacksonUtil.writeJsonValue(patient),
                message = "Failed to transform Patient"
            )

        return publishResources(
            tenantMnemonic = tenantMnemonic,
            resourceList = listOf(transformedPatient),
            resourceType = "Patient",
            successDataMap = mapOf(MirthKey.PATIENT_FHIR_ID.code to transformedPatient.id!!.value)
        )
    }
}
