package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.RequestGroupService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.RequestGroup
import com.projectronin.interop.fhir.ronin.resource.RoninRequestGroup
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.PublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishReferenceResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.mirth.models.destination.DestinationConfiguration
import com.projectronin.interop.mirth.models.destination.JavaScriptDestinationConfiguration
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

    override fun getConfiguration(): DestinationConfiguration =
        JavaScriptDestinationConfiguration(name = "Publish Request Groups")

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<RequestGroup> {
        return CarePlanPublishRequestGroupRequest(events, vendorFactory.requestGroupService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<RequestGroup> {
        return LoadRequestGroupRequest(events, vendorFactory.requestGroupService, tenant)
    }

    internal class CarePlanPublishRequestGroupRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: RequestGroupService,
        override val tenant: Tenant
    ) : PublishReferenceResourceRequest<RequestGroup>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { CarePlanPublishEvent(it, tenant) }

        private class CarePlanPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<CarePlan>(publishEvent, CarePlan::class) {
            override val requestKeys: Set<ResourceRequestKey> = sourceResource.activity
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
                .toSet()
        }
    }

    internal class LoadRequestGroupRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: RequestGroupService,
        tenant: Tenant
    ) : LoadResourceRequest<RequestGroup>(loadEvents, tenant)
}
