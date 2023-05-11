package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.aidbox.utils.findFhirID
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.ObservationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninObservations
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.util.unlocalize
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class ObservationPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninObservations
) : KafkaEventResourcePublisher<Observation>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {

    // turn a kafka event into an abstract class we can deal with
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Observation> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                when (event.resourceType) {
                    ResourceType.Patient ->
                        PatientSourceObservationLoadRequest(event, vendorFactory.observationService, tenant)

                    ResourceType.Condition ->
                        ConditionSourceObservationLoadRequest(event, vendorFactory.observationService, tenant)

                    else -> throw IllegalStateException("Received resource type that cannot be used to load observations")
                }
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                ObservationLoadRequest(event, vendorFactory.observationService, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientSourceObservationLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: ObservationService,
        override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<Observation>(sourceEvent) {

        private val categoryValueSet = CodeSystem.OBSERVATION_CATEGORY.uri.value
        override fun loadResources(): List<Observation> {
            val patientFhirId = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class)
                .identifier
                .findFhirID()
            return fhirService.findObservationsByPatientAndCategory(
                tenant,
                listOf(
                    patientFhirId
                ),
                listOf(
                    FHIRSearchToken(categoryValueSet, ObservationCategoryCodes.VITAL_SIGNS.code),
                    FHIRSearchToken(categoryValueSet, ObservationCategoryCodes.LABORATORY.code)
                )
            )
        }
    }

    private class ConditionSourceObservationLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: ObservationService,
        override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<Observation>(sourceEvent) {
        override fun loadResources(): List<Observation> {
            val condition = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Condition::class)

            return condition.stage.map { stage ->
                stage.assessment
                    .filter { reference -> reference.isForType(fhirService.fhirResourceType.simpleName) }
                    .map { reference ->
                        // decomposedId should never return null once we've filtered on observation type
                        val observationFhirId = reference.decomposedId()!!.unlocalize(tenant)
                        fhirService.getByID(tenant, observationFhirId)
                    }
            }.flatten()
        }
    }

    private class ObservationLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: ObservationService,
        override val tenant: Tenant
    ) : LoadEventResourceLoadRequest<Observation>(sourceEvent)
}
