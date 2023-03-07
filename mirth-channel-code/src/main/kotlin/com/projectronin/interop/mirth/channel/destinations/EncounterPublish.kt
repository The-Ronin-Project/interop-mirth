package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.aidbox.utils.findFhirID
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.EncounterService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninEncounter
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class EncounterPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninEncounter,
) : KafkaEventResourcePublisher<Encounter>(
    tenantService, ehrFactory, transformManager, publishService, profileTransformer
) {

    // turn a kafka event into an abstract class we can deal with
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Encounter> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                PatientSourceEncounterLoadRequest(event, vendorFactory.encounterService, tenant)
            }
            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                EncounterLoadRequest(event, vendorFactory.encounterService, tenant)
            }
            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientSourceEncounterLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: EncounterService,
        override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<Encounter>(sourceEvent) {

        override fun loadResources(): List<Encounter> {
            val patientFhirId = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class)
                .identifier
                .findFhirID()
            return fhirService.findPatientEncounters(
                tenant,
                patientFhirId,
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusMonths(1)
            )
        }
    }

    private class EncounterLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: EncounterService,
        override val tenant: Tenant
    ) : LoadEventResourceLoadRequest<Encounter>(sourceEvent)
}
