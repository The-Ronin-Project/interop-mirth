package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.util.unlocalize
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
        PublishEventResourceLoadRequest<Practitioner, Appointment>(sourceEvent) {
        override val sourceResource: Appointment =
            JacksonUtil.readJsonObject(sourceEvent.resourceJson, Appointment::class)

        override val requestKeys: List<ResourceRequestKey> = sourceResource.participant
            .asSequence()
            .filter { it.actor?.decomposedType()?.startsWith("Practitioner") == true }
            .mapNotNull { it.actor?.decomposedId() }
            .distinct().map { ResourceRequestKey(metadata.runId, ResourceType.Practitioner, tenant, it) }
            .toList()

        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<Practitioner> {
            val practitionerFhirIds = requestKeys.map { it.resourceId.unlocalize(tenant) }

            return practitionerFhirIds.map { fhirID ->
                fhirService.getPractitioner(tenant, fhirID)
            }
        }
    }

    private class PractitionerLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: PractitionerService,
        tenant: Tenant
    ) : LoadEventResourceLoadRequest<Practitioner>(sourceEvent, tenant)
}
