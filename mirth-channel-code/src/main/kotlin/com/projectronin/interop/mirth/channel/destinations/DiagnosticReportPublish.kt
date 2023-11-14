package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.DiagnosticReportService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.DiagnosticReport
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninDiagnosticReports
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
import java.time.OffsetDateTime

@Component
class DiagnosticReportPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninDiagnosticReports
) : KafkaEventResourcePublisher<DiagnosticReport>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {
    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<DiagnosticReport> {
        return PatientPublishDiagnosticReportRequest(events, vendorFactory.diagnosticReportService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<DiagnosticReport> {
        return LoadDiagnosticReportRequest(events, vendorFactory.diagnosticReportService, tenant)
    }

    internal class PatientPublishDiagnosticReportRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: DiagnosticReportService,
        override val tenant: Tenant
    ) : PublishResourceRequest<DiagnosticReport>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?
        ): Map<String, List<DiagnosticReport>> {
            return requestFhirIds.associateWith {
                fhirService.getDiagnosticReportByPatient(
                    tenant,
                    it,
                    startDate?.toLocalDate(),
                    endDate?.toLocalDate()
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class LoadDiagnosticReportRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: DiagnosticReportService,
        tenant: Tenant
    ) : LoadResourceRequest<DiagnosticReport>(loadEvents, tenant)
}
