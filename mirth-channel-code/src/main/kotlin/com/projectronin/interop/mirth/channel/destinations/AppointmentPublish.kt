package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.AppointmentService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AppointmentPublish(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val transformManager: TransformManager,
    private val roninAppointment: RoninAppointment,
    private val publishService: PublishService,
) : TenantlessDestinationService() {

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val eventClassName = sourceMap[MirthKey.KAFKA_EVENT.code]
            ?: throw MapVariableMissing("Missing Event Name")
        val appointmentLoadRequest = convertEventToRequest(msg, eventClassName as String)
        val resources = runCatching {
            appointmentLoadRequest.loadAppointments(tenant, vendorFactory.appointmentService)
        }.fold(
            onSuccess = { it },
            onFailure = {
                logger.error(it) { "Failed to retrieve appointments from EHR" }
                return MirthResponse(
                    status = MirthResponseStatus.ERROR,
                    detailedMessage = it.message,
                    message = "Failed EHR Call"
                )
            }
        )

        val transformedResources = resources.mapNotNull {
            transformManager.transformResource(it, roninAppointment, tenant)
        }
        if (transformedResources.isEmpty()) {
            logger.error { "Failed to transform ${resources.size} appointment(s)" }
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = resources.truncateList(),
                message = "Failed to transform ${resources.size} appointment(s)"
            )
        }

        if (!publishService.publishFHIRResources(
                tenantMnemonic,
                transformedResources,
                appointmentLoadRequest.dataTrigger
            )
        ) {
            logger.error { "Failed to publish ${transformedResources.size} appointment(s)" }
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = transformedResources.truncateList(),
                message = "Failed to publish ${transformedResources.size} appointment(s)"
            )
        }

        return MirthResponse(
            status = MirthResponseStatus.SENT,
            detailedMessage = transformedResources.truncateList(),
            message = "Published ${transformedResources.size} appointment(s)",
        )
    }

    fun List<Appointment>.truncateList(): String {
        val list = when {
            this.size > 5 -> this.map { it.id?.value }
            else -> this
        }
        return JacksonUtil.writeJsonValue(list)
    }

    private fun convertEventToRequest(msg: String, eventClassName: String): AppointmentLoadRequest {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(msg, InteropResourcePublishV1::class)
                PatientSourceAppointmentLoad(event)
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(msg, InteropResourceLoadV1::class)
                AdHocSourceAppointmentLoad(event)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    abstract class AppointmentLoadRequest {
        abstract val sourceEvent: Any
        abstract val dataTrigger: DataTrigger
        abstract fun loadAppointments(tenant: Tenant, appointmentService: AppointmentService): List<Appointment>
    }

    private class PatientSourceAppointmentLoad(
        override val sourceEvent: InteropResourcePublishV1
    ) : AppointmentLoadRequest() {

        override val dataTrigger: DataTrigger = when (sourceEvent.dataTrigger) {
            InteropResourcePublishV1.DataTrigger.adhoc -> DataTrigger.AD_HOC
            InteropResourcePublishV1.DataTrigger.nightly -> DataTrigger.NIGHTLY
            else -> {
                throw IllegalStateException("Received a data trigger which cannot be transformed to a known value")
            }
        }

        override fun loadAppointments(tenant: Tenant, appointmentService: AppointmentService): List<Appointment> {
            val patientFhirId = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class).id?.value
            return appointmentService.findPatientAppointments(
                tenant,
                patientFhirId!!.unlocalize(tenant),
                startDate = LocalDate.now(),
                endDate = LocalDate.now().plusMonths(3),
            )
        }
    }

    private class AdHocSourceAppointmentLoad(
        override val sourceEvent: InteropResourceLoadV1,
    ) : AppointmentLoadRequest() {
        override val dataTrigger: DataTrigger = DataTrigger.AD_HOC
        override fun loadAppointments(tenant: Tenant, appointmentService: AppointmentService): List<Appointment> {
            return listOf(appointmentService.getByID(tenant, sourceEvent.resourceFHIRId))
        }
    }
}
