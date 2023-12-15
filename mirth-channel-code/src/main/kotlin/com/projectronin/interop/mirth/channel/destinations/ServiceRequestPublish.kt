package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.ServiceRequestService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.DiagnosticReport
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Procedure
import com.projectronin.interop.fhir.r4.resource.ServiceRequest
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
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
import java.time.OffsetDateTime

@Component
class ServiceRequestPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
) : KafkaEventResourcePublisher<ServiceRequest>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
    ) {
    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<ServiceRequest> {
        // Only events for the same resource type are grouped, so just peek at the first one
        return when (val resourceType = events.first().resourceType) {
            ResourceType.Patient ->
                PatientPublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            ResourceType.MedicationRequest ->
                MedicationRequestPublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            ResourceType.Encounter ->
                EncounterPublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            ResourceType.Appointment ->
                AppointmentPublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            ResourceType.DiagnosticReport ->
                DiagnosticReportPublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            ResourceType.MedicationStatement ->
                MedicationStatementPublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            ResourceType.Observation ->
                ObservationPublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            ResourceType.Procedure ->
                ProcedurePublishServiceRequestRequest(
                    events,
                    vendorFactory.serviceRequestService,
                    tenant,
                )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load service requests")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<ServiceRequest> {
        return LoadServiceRequestRequest(events, vendorFactory.serviceRequestService, tenant)
    }

    internal class PatientPublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<ServiceRequest>> {
            return requestFhirIds.associateWith {
                fhirService.getServiceRequestsForPatient(
                    tenant,
                    it,
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class MedicationRequestPublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { MedicationRequestPublishEvent(it, tenant) }

        private class MedicationRequestPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<MedicationRequest>(publishEvent, MedicationRequest::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.basedOn.asSequence()
                    .filter { it.isForType("ServiceRequest") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.ServiceRequest, tenant, it) }.toSet()
        }
    }

    internal class EncounterPublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { EncounterPublishEvent(it, tenant) }

        private class EncounterPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Encounter>(publishEvent, Encounter::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.basedOn.asSequence()
                    .filter { it.isForType("ServiceRequest") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.ServiceRequest, tenant, it) }.toSet()
        }
    }

    internal class AppointmentPublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { AppointmentPublishEvent(it, tenant) }

        private class AppointmentPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Appointment>(publishEvent, Appointment::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.basedOn.asSequence()
                    .filter { it.isForType("ServiceRequest") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.ServiceRequest, tenant, it) }.toSet()
        }
    }

    internal class DiagnosticReportPublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { DiagnosticReportPublishEvent(it, tenant) }

        private class DiagnosticReportPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<DiagnosticReport>(publishEvent, DiagnosticReport::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.basedOn.asSequence()
                    .filter { it.isForType("ServiceRequest") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.ServiceRequest, tenant, it) }.toSet()
        }
    }

    internal class MedicationStatementPublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { MedicationStatementPublishEvent(it, tenant) }

        private class MedicationStatementPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<MedicationStatement>(publishEvent, MedicationStatement::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.basedOn.asSequence()
                    .filter { it.isForType("ServiceRequest") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.ServiceRequest, tenant, it) }.toSet()
        }
    }

    internal class ObservationPublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { ObservationPublishEvent(it, tenant) }

        private class ObservationPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Observation>(publishEvent, Observation::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.basedOn.asSequence()
                    .filter { it.isForType("ServiceRequest") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.ServiceRequest, tenant, it) }.toSet()
        }
    }

    internal class ProcedurePublishServiceRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ServiceRequestService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<ServiceRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { ProcedurePublishEvent(it, tenant) }

        private class ProcedurePublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Procedure>(publishEvent, Procedure::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.basedOn.asSequence()
                    .filter { it.isForType("ServiceRequest") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.ServiceRequest, tenant, it) }.toSet()
        }
    }

    internal class LoadServiceRequestRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: ServiceRequestService,
        tenant: Tenant,
    ) : LoadResourceRequest<ServiceRequest>(loadEvents, tenant)
}
