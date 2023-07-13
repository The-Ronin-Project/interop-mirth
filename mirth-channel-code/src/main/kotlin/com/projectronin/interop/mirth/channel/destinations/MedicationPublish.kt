package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.MedicationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninMedication
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.LoadEventResourceLoadRequest
import com.projectronin.interop.mirth.channel.base.kafka.PublishEventResourceLoadRequest
import com.projectronin.interop.mirth.channel.base.kafka.ResourceLoadRequest
import com.projectronin.interop.mirth.channel.base.kafka.ResourceRequestKey
import com.projectronin.interop.mirth.channel.util.unlocalize
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

    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Medication> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                when (event.resourceType) {
                    ResourceType.MedicationRequest ->
                        MedicationRequestSourceMedicationLoadRequest(
                            event,
                            vendorFactory.medicationService,
                            tenant
                        )
                    ResourceType.Medication ->
                        MedicationSourceMedicationLoadRequest(event, vendorFactory.medicationService, tenant)
                    else -> throw IllegalStateException("Received resource type that cannot be used to load medications")
                }
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                MedicationLoadRequest(event, vendorFactory.medicationService, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class MedicationSourceMedicationLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: MedicationService,
        override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<Medication, Medication>(sourceEvent) {
        override val sourceResource: Medication = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Medication::class)
        override val requestKeys: List<ResourceRequestKey> by lazy {
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
            }
        }
        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<Medication> {
            return requestKeys.map {
                fhirService.getByID(
                    tenant,
                    it.resourceId.unlocalize(tenant)
                )
            }
        }
    }

    private class MedicationRequestSourceMedicationLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: MedicationService,
        override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<Medication, MedicationRequest>(sourceEvent) {
        override val sourceResource: MedicationRequest = JacksonUtil.readJsonObject(sourceEvent.resourceJson, MedicationRequest::class)
        override val requestKeys: List<ResourceRequestKey> by lazy {
            val medication = sourceResource.medication!!
            val medicationId = medication.let {
                if (medication.type == DynamicValueType.REFERENCE) {
                    val medicationReference = (medication.value as Reference)
                    if (medicationReference.decomposedType() == "Medication") {
                        return@let medicationReference.decomposedId()!!
                    }
                }
                return@lazy emptyList()
            }
            listOf(
                ResourceRequestKey(
                    metadata.runId,
                    ResourceType.Medication,
                    tenant,
                    medicationId
                )
            )
        }

        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<Medication> {
            return requestKeys.map {
                fhirService.getByID(
                    tenant,
                    it.resourceId.unlocalize(tenant)
                )
            }
        }
    }

    private class MedicationLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: MedicationService,
        tenant: Tenant
    ) :
        LoadEventResourceLoadRequest<Medication>(sourceEvent, tenant)
}
