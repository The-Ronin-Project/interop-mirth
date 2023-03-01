package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
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
    profileTransformer: RoninPractitioner,
) : KafkaEventResourcePublisher<Practitioner>(
    tenantService, ehrFactory, transformManager, publishService, profileTransformer
) {

    // turn a kafka event into an abstract class we can deal with
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Practitioner> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                AppointmentSourcePractitionerLoadRequest(event, vendorFactory.practitionerService, tenant)
            }
            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                PractitionerLoadRequest(event, vendorFactory.practitionerService, tenant)
            }
            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class AppointmentSourcePractitionerLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: PractitionerService,
        override val tenant: Tenant
    ) :
        PublishEventResourceLoadRequest<Practitioner>(sourceEvent) {

        override fun loadResources(): List<Practitioner> {
            return JacksonUtil.readJsonObject(sourceEvent.resourceJson, Appointment::class)
                .participant
                .mapNotNull { it.actor?.reference?.value }
                .filter { it.contains("Practitioner/") }
                .map { fhirService.getPractitioner(tenant, it.split("/")[1]) }
                .toList()
        }
    }

    private class PractitionerLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: PractitionerService,
        override val tenant: Tenant
    ) : LoadEventResourceLoadRequest<Practitioner>(sourceEvent)
}
