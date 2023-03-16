package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.AppointmentService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AppointmentPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninAppointment
) : KafkaEventResourcePublisher<Appointment>(
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
    ): ResourceLoadRequest<Appointment> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                PatientSourceAppointmentLoadRequest(event, vendorFactory.appointmentService, tenant)
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                AppointmentLoadRequest(event, vendorFactory.appointmentService, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientSourceAppointmentLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: AppointmentService,
        override val tenant: Tenant
    ) :
        PublishEventResourceLoadRequest<Appointment>(sourceEvent) {

        override fun loadResources(): List<Appointment> {
            val patientFhirId = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class).id?.value
            return fhirService.findPatientAppointments(
                tenant,
                patientFhirId!!.unlocalize(tenant),
                startDate = LocalDate.now().minusMonths(1),
                endDate = LocalDate.now().plusMonths(1)
            )
        }
    }

    private class AppointmentLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: AppointmentService,
        override val tenant: Tenant
    ) :
        LoadEventResourceLoadRequest<Appointment>(sourceEvent)
}
