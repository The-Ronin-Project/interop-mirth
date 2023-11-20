package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.DestinationConfiguration
import com.projectronin.interop.mirth.channel.base.JavaScriptDestinationConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.PublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishReferenceResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class PractitionerPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninPractitioner
) : KafkaEventResourcePublisher<Practitioner>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {
    override val cacheAndCompareResults: Boolean = true

    override fun getConfiguration(): DestinationConfiguration =
        JavaScriptDestinationConfiguration(name = "Publish Practitioners")

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<Practitioner> {
        return AppointmentPublishPractitionerRequest(events, vendorFactory.practitionerService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<Practitioner> {
        return LoadPractitionerRequest(events, vendorFactory.practitionerService, tenant)
    }

    internal class AppointmentPublishPractitionerRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: PractitionerService,
        override val tenant: Tenant
    ) : PublishReferenceResourceRequest<Practitioner>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { AppointmentPublishEvent(it, tenant) }

        private class AppointmentPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Appointment>(publishEvent, Appointment::class) {
            override val requestKeys: Set<ResourceRequestKey> = sourceResource.participant
                .asSequence()
                .filter { it.actor?.decomposedType()?.startsWith("Practitioner") == true }
                .mapNotNull { it.actor?.decomposedId() }
                .distinct().map {
                    ResourceRequestKey(
                        metadata.runId,
                        ResourceType.Practitioner,
                        tenant,
                        it
                    )
                }
                .toSet()
        }
    }

    internal class LoadPractitionerRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: PractitionerService,
        tenant: Tenant
    ) : LoadResourceRequest<Practitioner>(loadEvents, tenant)
}
