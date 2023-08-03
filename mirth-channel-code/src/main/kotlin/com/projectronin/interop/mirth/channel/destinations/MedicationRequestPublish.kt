package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.MedicationRequestService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationRequest
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class MedicationRequestPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninMedicationRequest
) : KafkaEventResourcePublisher<MedicationRequest>(
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
    ): PublishResourceRequest<MedicationRequest> {
        return PatientPublishMedicationRequestRequest(events, vendorFactory.medicationRequestService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<MedicationRequest> {
        return LoadMedicationRequestRequest(events, vendorFactory.medicationRequestService, tenant)
    }

    internal class PatientPublishMedicationRequestRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: MedicationRequestService,
        override val tenant: Tenant
    ) : PublishResourceRequest<MedicationRequest>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(requestFhirIds: List<String>): Map<String, List<MedicationRequest>> {
            return requestFhirIds.associateWith { fhirService.getMedicationRequestByPatient(tenant, it) }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class LoadMedicationRequestRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: MedicationRequestService,
        tenant: Tenant
    ) : LoadResourceRequest<MedicationRequest>(loadEvents, tenant)
}
