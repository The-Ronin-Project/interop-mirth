package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.ProcedureService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Procedure
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
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class ProcedurePublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
) : KafkaEventResourcePublisher<Procedure>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
    ) {
    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<Procedure> {
        // Only events for the same resource type are grouped, so just peek at the first one
        return when (val resourceType = events.first().resourceType) {
            ResourceType.Patient ->
                PatientPublishProcedureRequest(events, vendorFactory.procedureService, tenant)

            ResourceType.Appointment ->
                AppointmentPublishProcedureRequest(
                    events,
                    vendorFactory.procedureService,
                    tenant,
                )

            ResourceType.Encounter ->
                EncounterPublishProcedureRequest(
                    events,
                    vendorFactory.procedureService,
                    tenant,
                )

            ResourceType.MedicationStatement ->
                MedicationStatementPublishProcedureRequest(
                    events,
                    vendorFactory.procedureService,
                    tenant,
                )

            ResourceType.Observation ->
                ObservationPublishProcedureRequest(
                    events,
                    vendorFactory.procedureService,
                    tenant,
                )

            ResourceType.Procedure ->
                ProcedurePublishProcedureRequest(
                    events,
                    vendorFactory.procedureService,
                    tenant,
                )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load procedures")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<Procedure> {
        return LoadProcedureRequest(events, vendorFactory.procedureService, tenant)
    }

    internal class PatientPublishProcedureRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ProcedureService,
        override val tenant: Tenant,
    ) : PublishResourceRequest<Procedure>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<Procedure>> {
            return requestFhirIds.associateWith { fhirId ->
                fhirService.getProcedureByPatient(
                    tenant,
                    fhirId,
                    startDate?.toLocalDate() ?: LocalDate.now().minusMonths(2),
                    endDate?.toLocalDate() ?: LocalDate.now(),
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class AppointmentPublishProcedureRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ProcedureService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<Procedure>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { AppointmentPublishEvent(it, tenant) }

        private class AppointmentPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Appointment>(publishEvent, Appointment::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.reasonReference.asSequence()
                    .filter { it.isForType("Procedure") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Procedure, tenant, it) }.toSet()
        }
    }

    internal class EncounterPublishProcedureRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ProcedureService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<Procedure>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { EncounterPublishEvent(it, tenant) }

        private class EncounterPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Encounter>(publishEvent, Encounter::class) {
            private val reasonReferenceKeys =
                sourceResource.reasonReference.asSequence()
                    .filter { it.isForType("Procedure") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Procedure, tenant, it) }.toSet()
            private val diagnosisKeys =
                sourceResource.diagnosis.asSequence()
                    .filter { it.condition?.decomposedType()?.startsWith("Procedure") == true }
                    .mapNotNull { it.condition?.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Procedure, tenant, it) }.toSet()
            override val requestKeys = reasonReferenceKeys + diagnosisKeys
        }
    }

    internal class MedicationStatementPublishProcedureRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ProcedureService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<Procedure>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { MedicationStatementPublishEvent(it, tenant) }

        private class MedicationStatementPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<MedicationStatement>(publishEvent, MedicationStatement::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.partOf.asSequence()
                    .filter { it.isForType("Procedure") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Procedure, tenant, it) }.toSet()
        }
    }

    internal class ObservationPublishProcedureRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ProcedureService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<Procedure>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { ObservationPublishEvent(it, tenant) }

        private class ObservationPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Observation>(publishEvent, Observation::class) {
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.partOf.asSequence()
                    .filter { it.isForType("Procedure") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Procedure, tenant, it) }.toSet()
        }
    }

    internal class ProcedurePublishProcedureRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ProcedureService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<Procedure>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { ProcedurePublishEvent(it, tenant) }

        private class ProcedurePublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<Procedure>(publishEvent, Procedure::class) {
            private val partOfKeys =
                sourceResource.partOf.asSequence()
                    .filter { it.isForType("Procedure") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Procedure, tenant, it) }.toSet()
            private val reasonReferenceKeys =
                sourceResource.reasonReference.asSequence()
                    .filter { it.isForType("Procedure") }
                    .mapNotNull { it.decomposedId() }.distinct()
                    .map { ResourceRequestKey(metadata.runId, ResourceType.Procedure, tenant, it) }.toSet()
            override val requestKeys = partOfKeys + reasonReferenceKeys
        }
    }

    internal class LoadProcedureRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: ProcedureService,
        tenant: Tenant,
    ) : LoadResourceRequest<Procedure>(loadEvents, tenant)
}
