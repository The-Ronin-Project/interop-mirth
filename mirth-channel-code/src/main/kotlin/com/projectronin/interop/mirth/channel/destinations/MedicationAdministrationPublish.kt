package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.MedicationAdministrationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.MedicationAdministration
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationAdministration
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.DestinationConfiguration
import com.projectronin.interop.mirth.channel.base.JavaScriptDestinationConfiguration
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
class MedicationAdministrationPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninMedicationAdministration
) : KafkaEventResourcePublisher<MedicationAdministration>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {
    override fun getConfiguration(): DestinationConfiguration =
        JavaScriptDestinationConfiguration(name = "Publish Medication Administrations")

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<MedicationAdministration> {
        // Only events for the same resource type are grouped, so just peek at the first one
        return when (val resourceType = events.first().resourceType) {
            ResourceType.Patient -> PatientPublishMedicationAdministrationRequest(
                events,
                vendorFactory.medicationAdministrationService,
                tenant
            )

            ResourceType.MedicationRequest -> MedicationRequestPublishMedicationAdministrationRequest(
                events,
                vendorFactory.medicationAdministrationService,
                tenant
            )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load medication administrations")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<MedicationAdministration> {
        return LoadMedicationAdministrationRequest(events, vendorFactory.medicationAdministrationService, tenant)
    }

    internal class PatientPublishMedicationAdministrationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: MedicationAdministrationService,
        override val tenant: Tenant
    ) : PublishResourceRequest<MedicationAdministration>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?
        ): Map<String, List<MedicationAdministration>> {
            return requestFhirIds.associateWith {
                fhirService.findMedicationAdministrationsByPatient(
                    tenant = tenant,
                    patientFHIRId = it,
                    startDate = startDate?.toLocalDate() ?: LocalDate.now().minusMonths(2),
                    endDate = endDate?.toLocalDate() ?: LocalDate.now()
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class MedicationRequestPublishMedicationAdministrationRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: MedicationAdministrationService,
        override val tenant: Tenant
    ) : PublishResourceRequest<MedicationAdministration>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { MedicationRequestPublishEvent(it, tenant) }

        private val medicationRequestById =
            sourceEvents.associate {
                val event = it as MedicationRequestPublishEvent
                event.requestKeys.first().unlocalizedResourceId to event.sourceResource
            }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?
        ): Map<String, List<MedicationAdministration>> {
            return requestFhirIds.associateWith {
                val medicationRequest = medicationRequestById[it]!!
                fhirService.findMedicationAdministrationsByRequest(tenant, medicationRequest)
            }
        }

        private class MedicationRequestPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<MedicationRequest>(publishEvent, tenant, MedicationRequest::class)
    }

    internal class LoadMedicationAdministrationRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: MedicationAdministrationService,
        tenant: Tenant
    ) : LoadResourceRequest<MedicationAdministration>(loadEvents, tenant)
}
