package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MirthClient
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.data.resources.practitioner
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

const val mdmQueueChannelName = "MDMQueueOut"

class MDMQueueTest : BaseMirthChannelTest(
    mdmQueueChannelName,
    emptyList(),
    listOf("DocumentReference", "Binary"),
) {
    // Personally, I'm sick of misspelling one of these and having my test blow up while developing
    private val documentReference = "DocumentReference"
    private val binary = "Binary"
    @Test
    fun `mdm queue can be sent`() {
        val mockEHRPractitioner = practitioner { }
        val mockEHRPatient = patient { }

        MockEHRTestData.add(mockEHRPractitioner)
        MockEHRTestData.add(mockEHRPatient)
        assertEquals(0, getMockEHRResourceCount(documentReference))
        assertEquals(0, getMockEHRResourceCount(binary))

        val aidboxPractitioner = mockEHRPractitioner.copy(
            identifier = mockEHRPractitioner.identifier + tenantIdentifier(testTenant)
        )
        val aidboxPatient = mockEHRPatient.copy(
            identifier = mockEHRPatient.identifier + tenantIdentifier(testTenant)
        )
        val aidboxPractitionerId = AidboxTestData.add(aidboxPractitioner)
        val aidboxPatientId = AidboxTestData.add(aidboxPatient)

        val noteInput = mapOf(
            "datetime" to "202206011250",
            "patientId" to aidboxPatientId,
            "patientIdType" to "FHIR",
            "practitionerFhirId" to aidboxPractitionerId,
            "noteText" to "integration testing\nsecond line",
            "isAlert" to false,
            "noteSender" to "PRACTITIONER"
        )
        val documentID = ProxyClient.sendNote(noteInput, testTenant)["data"]["sendNote"]
        assertNotNull(documentID)

        deployAndStartChannel(true)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        assertEquals(1, getMockEHRResourceCount(documentReference))
        assertEquals(1, getMockEHRResourceCount(binary))

        /*
        currently, we don't have FHIR objects for DocumentReference or Binary in FHIR
        (in mockEHR they're off the hapi models), in the future, if we do have those object
        you could cast these to those object so these checks became easier
         */
        val document = MockEHRClient.getAllResources(documentReference).get("entry")[0].get("resource")

        assertEquals(documentID, document.get("identifier")[0].get("value"))
        val binaryId = document.get("content")[0].get("attachment").get("url").asText().substringAfterLast("/")
        val binary = MockEHRClient.getPlainBinary(binaryId)

        assertEquals("integration testing\nsecond line", binary)
    }

    @Test
    fun `no data no message`() {
        assertEquals(0, getMockEHRResourceCount(documentReference))
        assertEquals(0, getMockEHRResourceCount(binary))
        // start channel
        deployAndStartChannel(false)
        // just wait a moment
        runBlocking {
            delay(1000)
        }
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(0, list.size)

        // nothing added
        assertEquals(0, getMockEHRResourceCount(documentReference))
        assertEquals(0, getMockEHRResourceCount(binary))
    }
}
