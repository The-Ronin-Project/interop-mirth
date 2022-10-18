package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.MirthClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.appointment
import com.projectronin.interop.mirth.channels.client.daysFromNow
import com.projectronin.interop.mirth.channels.client.identifier
import com.projectronin.interop.mirth.channels.client.name
import com.projectronin.interop.mirth.channels.client.participant
import com.projectronin.interop.mirth.channels.client.patient
import com.projectronin.interop.mirth.channels.client.reference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

const val appointmentQueueChannelName = "AppointmentQueue"

class AppointmentQueueTest :
    BaseMirthChannelTest(
        appointmentQueueChannelName,
        listOf("Patient", "Appointment")
    ) {

    @Test
    fun `appointments can be queued`() {
        val startDate = 2.daysFromNow()
        val endDate = 3.daysFromNow()
        val patient1 = patient {
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
                    value of "MRN123"
                }
            )
            name of listOf(
                name {
                    use of "usual" // This is required to generate the Epic response.
                }
            )
            gender of "male"
        }
        val patient1Id = MockEHRTestData.add(patient1)

        val appointment1 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patient1Id)
                }
            )
            start of startDate
            end of endDate
        }
        val appointment1Id = MockEHRTestData.add(appointment1)

        assertEquals(0, getAidboxResourceCount("Patient"))
        assertEquals(0, getAidboxResourceCount("Appointment"))

        // query for appointments from 'EHR'
        val apptNode = ProxyClient.getAppointmentsByMRN(
            "MRN123",
            testTenant,
            LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(3)
        )

        // start channel
        deployAndStartChannel(false)

        assertEquals(
            "\"$testTenant-$appointment1Id\"",
            apptNode["data"]["appointmentsByMRNAndDate"][0]["id"].toString()
        )

        // make sure a message queued in mirth
        waitForMessage(1)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        // appointment successfully added to Aidbox
        assertEquals(1, getAidboxResourceCount("Appointment"))
    }
}
