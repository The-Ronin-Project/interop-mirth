package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.destinations.AppointmentByPractitionerAppointmentWriter
import com.projectronin.interop.mirth.channel.destinations.AppointmentByPractitionerConditionWriter
import com.projectronin.interop.mirth.channel.destinations.AppointmentByPractitionerPatientWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.util.generateSerializedMetadata
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import org.springframework.stereotype.Component
import java.time.LocalDate

private const val APPOINTMENT_SERVICE = "appointments"
private const val CONDITION_SERVICE = "conditions"
private const val PATIENT_SERVICE = "patient"

/**
 *  This channel's goal is to populate upcoming patient, appointment and condition data.
 *  From providers in the Ronin clinical data store for a given tenant,
 *  this channel retrieves future appointment information and associated patients and conditions.
 */
@Component
class AppointmentByPractitionerNightlyLoad(
    tenantService: TenantService,
    transformManager: TransformManager,
    private val ehrFactory: EHRFactory,
    private val tenantConfigurationService: TenantConfigurationService,
    patientWriter: AppointmentByPractitionerPatientWriter,
    conditionWriter: AppointmentByPractitionerConditionWriter,
    appointmentWriter: AppointmentByPractitionerAppointmentWriter
) : ChannelService(tenantService, transformManager) {
    companion object : ChannelFactory<AppointmentByPractitionerNightlyLoad>()

    override val rootName = "AppointmentByPractitionerLoad"
    override val destinations = mapOf(
        PATIENT_SERVICE to patientWriter,
        CONDITION_SERVICE to conditionWriter,
        APPOINTMENT_SERVICE to appointmentWriter
    )
    private val futureDateRange: Long = 7
    private val pastDateRange: Long = 1

    /** retrieve from the Ronin clinical data store all providers for a given [tenantMnemonic],
     * then call the vendor with those identifiers to retrieve
     * all future appointments in the next [futureDateRange]. Then split those up by patient into [MirthMessage]s.
     */
    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        val currentDate = LocalDate.now()
        val endDate = currentDate.plusDays(futureDateRange)
        val startDate = currentDate.minusDays(pastDateRange)
        val tenant = getTenant(tenantMnemonic)
        val vendorFactory = ehrFactory.getVendorFactory(tenant)

        val locationIdsList = tenantConfigurationService.getLocationIDsByTenant(tenantMnemonic)
        if (locationIdsList.isEmpty()) {
            throw ResourcesNotFoundException("No Location IDs configured for tenant $tenantMnemonic")
        }

        val fullAppointments = vendorFactory.appointmentService.findLocationAppointments(
            tenant,
            locationIdsList,
            startDate,
            endDate
        )

        try {
            val perPatientAppointments = fullAppointments.appointments.groupBy { appointment ->
                appointment.participant.single {
                    it.actor?.reference?.value?.contains("Patient") == true
                }.actor!!.reference!!.value!!.removePrefix("Patient/")
            }
            return perPatientAppointments.map { patientList ->
                // see if we got any new patient objects from appointment service
                val patient = fullAppointments.newPatients?.find { it.id?.value == patientList.key }
                patientList.value.chunked(confirmMaxChunkSize(serviceMap)).map { appointments ->
                    val sourceMap = mutableMapOf(
                        MirthKey.PATIENT_FHIR_ID.code to patientList.key,
                        MirthKey.EVENT_METADATA.code to generateSerializedMetadata()
                    )
                    patient?.let { sourceMap.put(MirthKey.NEW_PATIENT_JSON.code, JacksonUtil.writeJsonValue(it)) }
                    MirthMessage(
                        JacksonUtil.writeJsonValue(appointments),
                        sourceMap
                    )
                }
            }.flatten()
        } catch (exception: NoSuchElementException) {
            return listOf(
                MirthMessage(
                    message = "Found Appointment with incomplete Patient reference",
                    emptyMap()
                )
            )
        }
    }
}
