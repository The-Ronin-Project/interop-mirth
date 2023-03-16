package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.LocationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
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
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Location> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                FhirIdSourceLocationLoadRequest(event, vendorFactory.locationService, tenant)
            }
            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                LocationLoadRequest(event, vendorFactory.locationService, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class FhirIdSourceLocationLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: LocationService,
        override val tenant: Tenant
    ) :
        PublishEventResourceLoadRequest<Location>(sourceEvent) {

        override fun loadResources(): List<Location> {
            val locationByAppointment =
                JacksonUtil.readJsonObject(sourceEvent.resourceJson, Appointment::class).participant
            val locationIds = locationByAppointment
                .filter { it.actor?.decomposedType()?.startsWith("Location") == true }
                .mapNotNull { it.actor?.decomposedId() }
                .map { it.unlocalize(tenant) }
                .distinct()
            val locations = fhirService.getLocationsByFHIRId(
                tenant,
                locationIds
            )
            return locations.values.toList()
        }
    }
    private class LocationLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: LocationService,
        override val tenant: Tenant
    ) :
        LoadEventResourceLoadRequest<Location>(sourceEvent)
}
