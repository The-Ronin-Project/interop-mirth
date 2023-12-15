package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.MedicationStatementService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.rcdm.transform.TransformManager
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class MedicationStatementPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
) : KafkaEventResourcePublisher<MedicationStatement>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
    ) {
    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<MedicationStatement> {
        return PatientPublishMedicationStatementRequest(events, vendorFactory.medicationStatementService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<MedicationStatement> {
        return LoadMedicationStatementRequest(events, vendorFactory.medicationStatementService, tenant)
    }

    internal class PatientPublishMedicationStatementRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: MedicationStatementService,
        override val tenant: Tenant,
    ) : PublishResourceRequest<MedicationStatement>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<MedicationStatement>> {
            return requestFhirIds.associateWith { fhirService.getMedicationStatementsByPatientFHIRId(tenant, it) }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class LoadMedicationStatementRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: MedicationStatementService,
        tenant: Tenant,
    ) : LoadResourceRequest<MedicationStatement>(loadEvents, tenant)
}
