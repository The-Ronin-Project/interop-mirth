package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.aidbox.utils.findFhirID
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.MedicationRequestService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninMedicationRequest
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
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
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<MedicationRequest> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                PatientSourceMedicationRequestLoad(event, vendorFactory.medicationRequestService, tenant)
            }
            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                MedicationRequestLoadRequest(event, vendorFactory.medicationRequestService, tenant)
            }
            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientSourceMedicationRequestLoad(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: MedicationRequestService,
        override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<MedicationRequest>(sourceEvent) {

        override fun loadResources(): List<MedicationRequest> {
            val patientFhirId = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class)
                .identifier
                .findFhirID()
            return fhirService.getMedicationRequestByPatient(
                tenant,
                patientFhirId
            )
        }
    }

    private class MedicationRequestLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: MedicationRequestService,
        override val tenant: Tenant
    ) :
        LoadEventResourceLoadRequest<MedicationRequest>(sourceEvent)
}
