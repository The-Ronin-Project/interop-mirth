package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.patient
import com.projectronin.interop.mirth.channels.client.practitioner
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

const val mdmQueueChannelName = "MDMQueueOut"

class MDMQueueTest : BaseMirthChannelTest(
    mdmQueueChannelName,
    listOf("Patient", "Practitioner"),
    listOf("Patient", "Practitioner", "DocumentReference", "Binary"),
) {
    // Personally, I'm sick of misspelling one of these and having my test blow up while developing
    private val patient = "Patient"
    private val practitioner = "Practitioner"
    private val documentReference = "DocumentReference"
    private val binary = "Binary"
    @Test
    fun `mdm queue can be sent`() {
        val mockEHRPractitioner = practitioner { }
        val mockEHRPatient = patient { }

        MockEHRTestData.add(mockEHRPractitioner)
        MockEHRTestData.add(mockEHRPatient)
        assertEquals(1, getMockEHRResourceCount(practitioner))
        assertEquals(1, getMockEHRResourceCount(patient))
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

        assertEquals(1, getAidboxResourceCount(practitioner))
        assertEquals(1, getAidboxResourceCount(patient))

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
        assertEquals(1, getMockEHRResourceCount(documentReference))
        assertEquals(1, getMockEHRResourceCount(binary))

        /* current, we don't have FHIR objects for DocumentReference or Binary in FHIR
        (in mockEHR they're off the hapi models), in the future, if we do have those object
        you could cast these to those object so these checks became easier
         */
        val document = MockEHRClient.getAllResources(documentReference).get("entry")[0].get("resource")

        assertEquals(documentID, document.get("identifier")[0].get("value"))
        val binaryId = document.get("content")[0].get("attachment").get("url").asText().substringAfterLast("/")
        val binary = MockEHRClient.getPlainBinary(binaryId)

        assertEquals("integration testing\nsecond line", binary)
    }
}
