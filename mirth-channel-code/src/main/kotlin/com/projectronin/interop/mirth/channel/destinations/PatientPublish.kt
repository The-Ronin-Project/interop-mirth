package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.PatientService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class PatientPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninPatient
) : KafkaEventResourcePublisher<Patient>(
    tenantService, ehrFactory, transformManager, publishService, profileTransformer
) {

    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Patient> {
        return when (eventClassName) {
            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                PatientLoadRequest(event, vendorFactory.patientService, tenant)
            }
            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: PatientService,
        override val tenant: Tenant
    ) : LoadEventResourceLoadRequest<Patient>(sourceEvent)
}
