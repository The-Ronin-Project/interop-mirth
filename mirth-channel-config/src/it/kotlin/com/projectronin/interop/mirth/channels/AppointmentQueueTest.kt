package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.datatypes.participant
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.primitives.daysFromNow
import com.projectronin.interop.mirth.channels.client.data.resources.appointment
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.random.Random

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
        // generating a random MRN so we can be sure if this test fails
        // the random patient we left in mockEHR / aidbox won't cause problems on future run of test
        val mrn = Random.nextInt(10000, 99999).toString()
        val patient1 = patient {
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
                    value of mrn
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
        val aidboxPatient = patient1.copy(
            identifier = patient1.identifier + tenantIdentifier(testTenant)
        )
        AidboxTestData.add(aidboxPatient)

        assertEquals(1, getAidboxResourceCount("Patient"))
        assertEquals(0, getAidboxResourceCount("Appointment"))

        // query for appointments from 'EHR'
        val apptNode = ProxyClient.getAppointmentsByMRN(
            mrn,
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
        assertAllConnectorsSent(list)

        // appointment successfully added to Aidbox
        assertEquals(1, getAidboxResourceCount("Appointment"))
    }

    @Test
    fun `no data no message`() {
        assertEquals(0, getAidboxResourceCount("Appointment"))
        // start channel
        deployAndStartChannel(false)
        // just wait a moment
        pause()
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(0, list.size)
        // nothing added
        assertEquals(0, getAidboxResourceCount("Appointment"))
    }
}
