package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.MedicationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninMedication
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
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
class MedicationPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninMedication
) : KafkaEventResourcePublisher<Medication>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {
    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<Medication> {
        // Only events for the same resource type are grouped, so just peek at the first one
        return when (val resourceType = events.first().resourceType) {
            ResourceType.MedicationRequest -> MedicationRequestPublishMedicationRequest(
                events,
                vendorFactory.medicationService,
                tenant
            )
            ResourceType.MedicationStatement -> MedicationStatementPublishMedicationRequest(
                events,
                vendorFactory.medicationService,
                tenant
            )
            ResourceType.Medication -> MedicationPublishMedicationRequest(
                events,
                vendorFactory.medicationService,
                tenant
            )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load medications")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<Medication> {
        return LoadMedicationRequest(events, vendorFactory.medicationService, tenant)
    }

    internal class MedicationPublishMedicationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: MedicationService,
        override val tenant: Tenant
    ) : PublishReferenceResourceRequest<Medication>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { MedicationPublishEvent(it, tenant) }

        private class MedicationPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Medication>(publishEvent, Medication::class) {
            override val requestKeys: Set<ResourceRequestKey> by lazy {
                val medicationIds = sourceResource.ingredient
                    .filter { it.item?.type == DynamicValueType.REFERENCE }
                    .mapNotNull {
                        val reference = it.item?.value as Reference
                        when (reference.decomposedType()) {
                            ResourceType.Medication.name -> reference.decomposedId()!!
                            else -> null
                        }
                    }
                medicationIds.map {
                    ResourceRequestKey(
                        metadata.runId,
                        ResourceType.Medication,
                        tenant,
                        it
                    )
                }.toSet()
            }
        }
    }

    internal class MedicationRequestPublishMedicationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: MedicationService,
        override val tenant: Tenant
    ) : PublishReferenceResourceRequest<Medication>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { MedicationRequestPublishEvent(it, tenant) }

        private class MedicationRequestPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<MedicationRequest>(publishEvent, MedicationRequest::class) {
            override val requestKeys: Set<ResourceRequestKey> by lazy {
                val medication = sourceResource.medication!!
                val medicationId = medication.let {
                    if (medication.type == DynamicValueType.REFERENCE) {
                        val medicationReference = (medication.value as Reference)
                        if (medicationReference.isForType(ResourceType.Medication)) {
                            return@let medicationReference.decomposedId()!!
                        }
                    }
                    return@lazy emptySet()
                }
                setOf(
                    ResourceRequestKey(
                        metadata.runId,
                        ResourceType.Medication,
                        tenant,
                        medicationId
                    )
                )
            }
        }
    }

    internal class MedicationStatementPublishMedicationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: MedicationService,
        override val tenant: Tenant
    ) : PublishReferenceResourceRequest<Medication>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { MedicationStatementPublishEvent(it, tenant) }

        private class MedicationStatementPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<MedicationStatement>(publishEvent, MedicationStatement::class) {
            override val requestKeys: Set<ResourceRequestKey> by lazy {
                val medication = sourceResource.medication!!
                val medicationId = medication.let {
                    if (medication.type == DynamicValueType.REFERENCE) {
                        val medicationReference = (medication.value as Reference)
                        if (medicationReference.isForType(ResourceType.Medication)) {
                            return@let medicationReference.decomposedId()!!
                        }
                    }
                    return@lazy emptySet()
                }
                setOf(
                    ResourceRequestKey(
                        metadata.runId,
                        ResourceType.Medication,
                        tenant,
                        medicationId
                    )
                )
            }
        }
    }

    internal class LoadMedicationRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: MedicationService,
        tenant: Tenant
    ) : LoadResourceRequest<Medication>(loadEvents, tenant)
}
