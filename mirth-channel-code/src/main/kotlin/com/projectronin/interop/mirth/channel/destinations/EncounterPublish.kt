package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.EncounterService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninEncounter
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class EncounterPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninEncounter,
) : KafkaEventResourcePublisher<Encounter>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
        profileTransformer,
    ) {
    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<Encounter> {
        return PatientPublishEncounterRequest(events, vendorFactory.encounterService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<Encounter> {
        return LoadEncounterRequest(events, vendorFactory.encounterService, tenant)
    }

    internal class PatientPublishEncounterRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: EncounterService,
        override val tenant: Tenant,
    ) : PublishResourceRequest<Encounter>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<Encounter>> {
            return requestFhirIds.associateWith {
                fhirService.findPatientEncounters(
                    tenant,
                    it,
                    startDate = startDate?.toLocalDate() ?: LocalDate.now().minusMonths(1),
                    endDate = endDate?.toLocalDate() ?: LocalDate.now().plusMonths(1),
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class LoadEncounterRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: EncounterService,
        tenant: Tenant,
    ) : LoadResourceRequest<Encounter>(loadEvents, tenant)
}
