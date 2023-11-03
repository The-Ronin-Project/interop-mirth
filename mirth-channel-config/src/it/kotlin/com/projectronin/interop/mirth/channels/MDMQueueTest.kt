package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.DocumentReference
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.time.Duration.Companion.seconds

class MDMQueueTest {
    val mdmQueueChannelName = "MDMQueueOut"
    private val channelName = mdmQueueChannelName

    // Personally, I'm sick of misspelling one of these and having my test blow up while developing
    private val documentReference = "DocumentReference"
    private val binary = "Binary"
    private val testChannelName = "$testTenant-$channelName"
    private val testChannelId = installChannel()

    @BeforeEach
    fun setup() {
        clearMessages()
        MockEHRClient.deleteAllResources(documentReference)
        MockEHRClient.deleteAllResources(binary)
    }

    @AfterEach
    fun tearDown() {
        MockEHRTestData.purge()
        AidboxTestData.purge()
    }
    private fun clearMessages() {
        MirthClient.clearAllStatistics()
        MirthClient.clearChannelMessages(testChannelId)
    }
    private fun installChannel(): String {
        val channelFile = File("channels/$channelName/channel/$channelName.xml")
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = documentBuilder.parse(channelFile)

        val channelId = document.getElementsByTagName("id").item(0).firstChild
        val updatedChannelId = "$testTenant-${channelId.textContent}".substring(0, maxChannelId)
        channelId.textContent = updatedChannelId

        val channelName = document.getElementsByTagName("name").item(0).firstChild
        channelName.textContent = testChannelName

        val domSource = DOMSource(document)
        val transformer = TransformerFactory.newInstance().newTransformer()

        val stringWriter = StringWriter()
        val streamResult = StreamResult(stringWriter)
        transformer.transform(domSource, streamResult)

        val modifiedXml = stringWriter.toString()
        MirthClient.putChannel(updatedChannelId, modifiedXml)
        MirthClient.enableChannel(updatedChannelId)

        return updatedChannelId
    }

    private fun deployAndStartChannel(waitForMessage: Boolean) {
        MirthClient.deployChannel(testChannelId)
        MirthClient.startChannel(testChannelId)

        if (waitForMessage) {
            runBlocking {
                withTimeout(timeout = 600.seconds) {
                    while (true) {
                        val count = MirthClient.getCompletedMessageCount(testChannelId)
                        if (count >= 1) {
                            // delay a moment to allow message to process, one destination might complete but give others a chance
                            delay(1000)
                            break
                        } else {
                            delay(1000)
                        }
                    }
                }
            }
        }
    }

    private fun getMockEHRResourceCount(resourceType: String): Int {
        val resources = MockEHRClient.getAllResources(resourceType)
        return resources.total?.value!!
    }

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
            identifier = mockEHRPatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patientId) +
                Identifier(type = CodeableConcepts.RONIN_MRN, system = CodeSystem.RONIN_MRN.uri, value = "Hl7ME".asFHIR()),
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
