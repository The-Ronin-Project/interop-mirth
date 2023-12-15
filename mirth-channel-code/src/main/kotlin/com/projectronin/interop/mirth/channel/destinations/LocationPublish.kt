package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.LocationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.PublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishReferenceResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.rcdm.transform.TransformManager
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class LocationPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
) : KafkaEventResourcePublisher<Location>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
    ) {
    override val cacheAndCompareResults: Boolean = true

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<Location> {
        // Only events for the same resource type are grouped, so just peek at the first one
        return when (val resourceType = events.first().resourceType) {
            ResourceType.Appointment ->
                AppointmentPublishLocationRequest(
                    events,
                    vendorFactory.locationService,
                    tenant,
                )

            ResourceType.Encounter ->
                EncounterPublishLocationRequest(
                    events,
                    vendorFactory.locationService,
                    tenant,
                )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load locations")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<Location> {
        return LoadLocationRequest(events, vendorFactory.locationService, tenant)
    }

    internal class AppointmentPublishLocationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: LocationService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<Location>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { AppointmentPublishEvent(it, tenant) }

        private class AppointmentPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Appointment>(publishEvent, Appointment::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.participant.asSequence()
                    .filter { it.actor?.decomposedType()?.startsWith("Location") == true }
                    .mapNotNull { it.actor?.decomposedId() }
                    .distinct().map {
                        ResourceRequestKey(
                            metadata.runId,
                            ResourceType.Location,
                            tenant,
                            it,
                        )
                    }.toSet()
        }
    }

    internal class EncounterPublishLocationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: LocationService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<Location>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { EncounterPublishEvent(it, tenant) }

        private class EncounterPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Encounter>(publishEvent, Encounter::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.location.mapNotNull { it.location?.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Location, tenant, it) }.toSet()
        }
    }

    internal class LoadLocationRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: LocationService,
        tenant: Tenant,
    ) : LoadResourceRequest<Location>(loadEvents, tenant)
}
