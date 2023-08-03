package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
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
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.PublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishReferenceResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.mirth.channel.util.isForType
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
    override val cacheAndCompareResults: Boolean = true

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<Observation> {
        // Only events for the same resource type are grouped, so just peek at the first one
        return when (val resourceType = events.first().resourceType) {
            ResourceType.Patient -> PatientPublishObservationRequest(events, vendorFactory.observationService, tenant)

            ResourceType.Condition -> ConditionPublishObservationRequest(
                events,
                vendorFactory.observationService,
                tenant
            )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load observations")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<Observation> {
        return LoadObservationRequest(events, vendorFactory.observationService, tenant)
    }

    internal class PatientPublishObservationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ObservationService,
        override val tenant: Tenant
    ) : PublishResourceRequest<Observation>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        private val categoryValueSet = CodeSystem.OBSERVATION_CATEGORY.uri.value
        override fun loadResourcesForIds(requestFhirIds: List<String>): Map<String, List<Observation>> {
            return requestFhirIds.associateWith {
                fhirService.findObservationsByPatientAndCategory(
                    tenant,
                    listOf(it),
                    listOf(
                        FHIRSearchToken(categoryValueSet, ObservationCategoryCodes.VITAL_SIGNS.code),
                        FHIRSearchToken(categoryValueSet, ObservationCategoryCodes.LABORATORY.code)
                    )
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class ConditionPublishObservationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ObservationService,
        override val tenant: Tenant
    ) : PublishReferenceResourceRequest<Observation>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { ConditionPublishEvent(it, tenant) }

        private class ConditionPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Condition>(publishEvent, Condition::class) {
            override val requestKeys: Set<ResourceRequestKey> = sourceResource.stage.map { stage ->
                stage.assessment.filter { reference -> reference.isForType(ResourceType.Observation) }
                    .map { reference ->
                        // decomposedId should never return null once we've filtered on observation type
                        reference.decomposedId()!!
                    }
            }.flatten().map {
                ResourceRequestKey(metadata.runId, ResourceType.Observation, tenant, it)
            }.toSet()
        }
    }

    internal class LoadObservationRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: ObservationService,
        tenant: Tenant
    ) : LoadResourceRequest<Observation>(loadEvents, tenant)
}
