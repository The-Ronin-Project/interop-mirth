package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.RequestGroupService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.RequestGroup
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninRequestGroup
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
class RequestGroupPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninRequestGroup
) : KafkaEventResourcePublisher<RequestGroup>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {
    override val cacheAndCompareResults: Boolean = true

    // turn a kafka event into an abstract class we can deal with
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<RequestGroup> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                CarePlanSourceRequestGroupLoadRequest(event, vendorFactory.requestGroupService, tenant)
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                RequestGroupLoadRequest(event, vendorFactory.requestGroupService, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class CarePlanSourceRequestGroupLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: RequestGroupService,
        override val tenant: Tenant
    ) :
        PublishEventResourceLoadRequest<RequestGroup, CarePlan>(sourceEvent) {
        override val sourceResource: CarePlan =
            JacksonUtil.readJsonObject(sourceEvent.resourceJson, CarePlan::class)

        override val requestKeys: List<ResourceRequestKey> = sourceResource.activity
            .asSequence()
            .filter { it.reference?.decomposedType()?.startsWith("RequestGroup") == true }
            .mapNotNull { it.reference?.decomposedId() }
            .distinct().map {
                ResourceRequestKey(
                    metadata.runId,
                    ResourceType.RequestGroup,
                    tenant,
                    it
                )
            }
            .toList()

        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<RequestGroup> {
            val requestGroupIds = requestKeys.map { it.resourceId.unlocalize(tenant) }

            return fhirService.getRequestGroupByFHIRId(tenant, requestGroupIds).map { it.value }
        }
    }

    private class RequestGroupLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: RequestGroupService,
        tenant: Tenant
    ) : LoadEventResourceLoadRequest<RequestGroup>(sourceEvent, tenant)
}
