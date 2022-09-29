package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.destinations.AppointmentByPractitionerAppointmentWriter
import com.projectronin.interop.mirth.channel.destinations.AppointmentByPractitionerConditionWriter
import com.projectronin.interop.mirth.channel.destinations.AppointmentByPractitionerPatientWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import java.time.LocalDate

private const val APPOINTMENT_SERVICE = "appointments"
private const val CONDITION_SERVICE = "conditions"
private const val PATIENT_SERVICE = "patient"

/**
 *  This channel's goal is to populate upcoming patient, appointment and condition data.
 *  From providers in the Ronin clinical data store for a given tenant,
 *  this channel retrieves future appointment information and associated patients and conditions.
 */
class AppointmentByPractitionerNightlyLoad(serviceFactory: ServiceFactory) : ChannelService(serviceFactory) {
    companion object : ChannelFactory<AppointmentByPractitionerNightlyLoad>()

    override val rootName = "AppointmentByPractitionerLoad"
    override val destinations = mapOf(
        PATIENT_SERVICE to AppointmentByPractitionerPatientWriter(rootName, serviceFactory),
        CONDITION_SERVICE to AppointmentByPractitionerConditionWriter(rootName, serviceFactory),
        APPOINTMENT_SERVICE to AppointmentByPractitionerAppointmentWriter(rootName, serviceFactory)
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
        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val vendorFactory = serviceFactory.vendorFactory(tenant)

        val practitionersMap = serviceFactory.practitionerService().getPractitionersByTenant(tenantMnemonic)
        if (practitionersMap.isEmpty()) {
            throw ResourcesNotFoundException("No Practitioners found in clinical data store for tenant $tenantMnemonic")
        }

        val fullAppointments = vendorFactory.appointmentService.findProviderAppointments(
            tenant,
            practitionersMap.map { FHIRIdentifiers(Id(it.key), it.value) },
            startDate,
            endDate
        )

        try {
            val perPatientAppointments = fullAppointments.appointments.groupBy { appointment ->
                appointment.participant.single {
                    it.actor?.reference?.contains("Patient") == true
                }.actor?.reference?.removePrefix("Patient/")
            }
            return perPatientAppointments.map { patientList ->
                // see if we got any new patient objects from appointment service
                val patient = fullAppointments.newPatients?.find { it.id?.value == patientList.key }
                patientList.value.chunked(confirmMaxChunkSize(serviceMap)).map { appointments ->
                    val sourceMap = mutableMapOf(MirthKey.PATIENT_FHIR_ID.code to patientList.key!!)
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
