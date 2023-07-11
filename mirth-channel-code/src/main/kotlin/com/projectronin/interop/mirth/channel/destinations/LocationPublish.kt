package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.LocationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
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
class LocationPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninLocation
) : KafkaEventResourcePublisher<Location>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {
    override val cacheAndCompareResults: Boolean = true

    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Location> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                when (event.resourceType) {
                    ResourceType.Appointment ->
                        AppointmentSourceLocationLoadRequest(event, vendorFactory.locationService, tenant)

                    ResourceType.Encounter ->
                        EncounterSourceLocationLoadRequest(event, vendorFactory.locationService, tenant)

                    else -> throw IllegalStateException("Received resource type that cannot be used to load locations")
                }
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                LocationLoadRequest(event, vendorFactory.locationService, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class AppointmentSourceLocationLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: LocationService,
        override val tenant: Tenant
    ) :
        PublishEventResourceLoadRequest<Location, Appointment>(sourceEvent) {
        override val sourceResource: Appointment =
            JacksonUtil.readJsonObject(sourceEvent.resourceJson, Appointment::class)

        override val requestKeys: List<ResourceRequestKey> =
            sourceResource.participant.asSequence()
                .filter { it.actor?.decomposedType()?.startsWith("Location") == true }
                .mapNotNull { it.actor?.decomposedId()?.unlocalize(tenant) }
                .distinct().map {
                    ResourceRequestKey(
                        metadata.runId,
                        ResourceType.Location,
                        tenant,
                        it
                    )
                }.toList()

        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<Location> {
            val locationIds = requestKeys.map { it.resourceId }
            val locations = fhirService.getLocationsByFHIRId(tenant, locationIds)
            return locations.values.toList()
        }
    }

    private class EncounterSourceLocationLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: LocationService,
        override val tenant: Tenant
    ) :
        PublishEventResourceLoadRequest<Location, Encounter>(sourceEvent) {
        override val sourceResource: Encounter = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Encounter::class)

        override val requestKeys: List<ResourceRequestKey> =
            sourceResource.location.mapNotNull { it.location?.decomposedId() }.distinct()
                .map { ResourceRequestKey(metadata.runId, ResourceType.Location, tenant, it) }

        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<Location> {
            val locationIds = requestKeys.map { it.resourceId.unlocalize(tenant) }
            val locations = fhirService.getLocationsByFHIRId(tenant, locationIds)
            return locations.values.toList()
        }
    }

    private class LocationLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: LocationService,
        tenant: Tenant
    ) :
        LoadEventResourceLoadRequest<Location>(sourceEvent, tenant)
}
