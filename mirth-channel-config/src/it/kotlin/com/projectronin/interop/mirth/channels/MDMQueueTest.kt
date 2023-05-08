package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

const val mdmQueueChannelName = "MDMQueueOut"

class MDMQueueTest : BaseMirthChannelTest(
    mdmQueueChannelName,
    emptyList(),
    listOf("DocumentReference", "Binary")
) {
    // Personally, I'm sick of misspelling one of these and having my test blow up while developing
    private val documentReference = "DocumentReference"
    private val binary = "Binary"

    @Test
    fun `mdm queue can be sent`() {
        val mockEHRPractitioner = practitioner { }
        val mockEHRPatient = patient { }

        val practitionerId = MockEHRTestData.add(mockEHRPractitioner)
        val patientId = MockEHRTestData.add(mockEHRPatient)
        assertEquals(0, getMockEHRResourceCount(documentReference))
        assertEquals(0, getMockEHRResourceCount(binary))

        val aidboxPractitioner = mockEHRPractitioner.copy(
            identifier = mockEHRPractitioner.identifier + tenantIdentifier(testTenant) + fhirIdentifier(practitionerId),
            id = Id("$testTenant-$practitionerId")
        )
        val aidboxPatient = mockEHRPatient.copy(
            identifier = mockEHRPatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patientId),
            id = Id("$testTenant-$patientId")
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
        val documentID = ProxyClient.sendNote(noteInput, testTenant)["data"]["sendNote"].asText()
        assertNotNull(documentID)

        deployAndStartChannel(true)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        assertEquals(1, getMockEHRResourceCount(documentReference))
        assertEquals(1, getMockEHRResourceCount(binary))

        val document = MockEHRClient.getAllResources(documentReference).entry.first().resource as DocumentReference
        assertEquals(documentID, document.identifier.first().value?.value)
        val binaryId = document.content.first().attachment?.url?.value?.substringAfterLast("/") ?: ""
        val binary = MockEHRClient.getPlainBinary(binaryId)

        assertEquals("integration testing\nsecond line", binary)
    }

    @Test
    fun `no data no message`() {
        assertEquals(0, getMockEHRResourceCount(documentReference))
        assertEquals(0, getMockEHRResourceCount(binary))
        // start channel
        deployAndStartChannel(false)
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(0, list.size)

        // nothing added
        assertEquals(0, getMockEHRResourceCount(documentReference))
        assertEquals(0, getMockEHRResourceCount(binary))
    }
}
