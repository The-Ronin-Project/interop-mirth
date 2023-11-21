package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.AppointmentService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.models.destination.DestinationConfiguration
import com.projectronin.interop.mirth.models.destination.JavaScriptDestinationConfiguration
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

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
    override fun getConfiguration(): DestinationConfiguration =
        JavaScriptDestinationConfiguration(name = "Publish Appointments")

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<Appointment> {
        return PatientPublishAppointmentRequest(events, vendorFactory.appointmentService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<Appointment> {
        return LoadAppointmentRequest(events, vendorFactory.appointmentService, tenant)
    }

    internal class PatientPublishAppointmentRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: AppointmentService,
        override val tenant: Tenant
    ) : PublishResourceRequest<Appointment>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?
        ): Map<String, List<Appointment>> {
            return requestFhirIds.associateWith {
                fhirService.findPatientAppointments(
                    tenant,
                    it,
                    startDate = startDate?.toLocalDate() ?: LocalDate.now().minusMonths(1),
                    endDate = endDate?.toLocalDate() ?: LocalDate.now().plusMonths(1)
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class LoadAppointmentRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: AppointmentService,
        tenant: Tenant
    ) : LoadResourceRequest<Appointment>(loadEvents, tenant)
}
