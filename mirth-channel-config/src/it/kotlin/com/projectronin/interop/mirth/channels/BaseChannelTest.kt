package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.mirth.channels.client.AidboxClient
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.io.StringWriter
import java.util.stream.Stream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.time.Duration.Companion.seconds

/**
 * Base class to handle testing an individual channel
 * takes a [channelName] to identify the channel,
 * [aidboxResourceTypes] a list of resource types to clear in aidbox and [mockEHRResourceTypes]
 * a list of resources  types to clear in mockERH
 */

@ExtendWith(TestListener::class)
abstract class BaseChannelTest(
    private val channelName: String,
    private val aidboxResourceTypes: List<String>,
    private val mockEHRResourceTypes: List<String> = emptyList(),
) {
    var tenantInUse = "NOTSET"
    protected val testChannelId = installChannel()
    protected open val groupId: String? = null

    @BeforeEach
    fun setup() {
        clearMessages()
        MockEHRTestData.purge()
        AidboxTestData.purge()
        deleteAidboxResources(*aidboxResourceTypes.toTypedArray())
        deleteMockEHRResources(*mockEHRResourceTypes.toTypedArray())
        MockOCIServerClient.client.clear("PutObjectExpectation")
    }

    @AfterEach
    fun tearDown() {
        MockEHRTestData.purge()
        AidboxTestData.purge()
        stopChannel()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        @AfterAll
        fun cleanup() {
            KafkaWrapper.kafkaPublishService.deleteAllPublishTopics()
            KafkaWrapper.kafkaLoadService.deleteAllLoadTopics()
        }

        @JvmStatic
        fun tenantsToTest(): Stream<String> {
            val tenants =
                try {
                    System.getenv("MIRTH_INTEGRATION_TEST_TENANTS").split(",")
                } catch (e: Exception) {
                    listOf()
                }.ifEmpty { listOf("epicmock", "cernmock") }

            return tenants.stream()
        }
    }

    protected fun installChannel(channelToInstall: String = channelName): String {
        val channelFile = File("channels/$channelToInstall/channel/$channelToInstall.xml")
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = documentBuilder.parse(channelFile)

        val channelId = document.getElementsByTagName("id").item(0).firstChild.textContent

        val domSource = DOMSource(document)
        val transformer = TransformerFactory.newInstance().newTransformer()

        val stringWriter = StringWriter()
        val streamResult = StreamResult(stringWriter)
        transformer.transform(domSource, streamResult)

        val modifiedXml = stringWriter.toString()
        MirthClient.putChannel(channelId, modifiedXml)
        MirthClient.enableChannel(channelId)

        return channelId
    }

    /**
     * Clears the messages and statistics for this channel. If the channel is currently running, it will be restarted.
     */
    protected fun clearMessages(channelToClear: String = testChannelId) {
        MirthClient.clearAllStatistics()
        MirthClient.clearChannelMessages(channelToClear)
    }

    protected fun deployAndStartChannel(
        waitForMessage: Boolean,
        timeout: Int = 60,
        channelToDeploy: String = testChannelId
    ) {
        MirthClient.deployChannel(channelToDeploy)
        MirthClient.startChannel(channelToDeploy)

        if (waitForMessage) {
            waitForMessage(1, timeout)
        }
    }

    protected fun stopChannel(channelToStop: String = testChannelId) {
        MirthClient.stopChannel(channelToStop)
    }

    protected fun getChannelMessageIds(): List<Int> {
        return MirthClient.getChannelMessageIds(testChannelId)
    }

    protected fun getAidboxResourceCount(resourceType: String): Int {
        val resources = AidboxClient.getAllResourcesForTenant(resourceType, tenantInUse)
        return resources.get("total").asInt()
    }

    protected fun getMockEHRResourceCount(resourceType: String): Int {
        val resources = MockEHRClient.getAllResources(resourceType)
        return resources.get("total").asInt()
    }

    protected fun deleteAidboxResources(vararg resourceTypes: String) {
        resourceTypes.forEach {
            tenantsToTest().forEach { tenant ->
                AidboxClient.deleteAllResources(it, tenant)
            }
        }
    }

    protected fun deleteMockEHRResources(vararg resourceTypes: String) {
        resourceTypes.forEach {
            MockEHRClient.deleteAllResources(it)
        }
    }

    protected fun assertAllConnectorsSent(messageList: List<Int>) {
        val messages = messageList.map {
            MirthClient.getMessageById(testChannelId, it)
        }
        messages.forEach { connectorMessage ->
            connectorMessage.destinationMessages.forEach {

                assertEquals(
                    "SENT",
                    it.status,
                    "status for connector ${it.connectorName} was not SENT. Actual status: ${it.status}"
                )
            }
        }
    }

    protected fun waitForMessage(minimumCount: Int, timeout: Int = 600) {
        runBlocking {
            withTimeout(timeout = timeout.seconds) {
                waitForMessage(minimumCount)
            }
        }
    }

    private suspend fun waitForMessage(minimumCount: Int) {
        while (true) {
            val count = MirthClient.getCompletedMessageCount(testChannelId)
            if (count >= minimumCount) {
                // delay a moment to allow message to process, one destination might complete but give others a chance
                delay(1000)
                break
            } else {
                delay(1000)
            }
        }
    }

    protected fun Resource<*>.getFhirIdentifier(): Identifier? {
        val identifiers = when (this::class) {
            Appointment::class -> (this as Appointment).identifier
            Condition::class -> (this as Condition).identifier
            Location::class -> (this as Location).identifier
            Observation::class -> (this as Observation).identifier
            Patient::class -> (this as Patient).identifier
            Practitioner::class -> (this as Practitioner).identifier
            PractitionerRole::class -> (this as PractitionerRole).identifier
            else -> throw IllegalStateException("Resource has not been cast or has no identifier field")
        }
        return identifiers.firstOrNull { it.system == CodeSystem.RONIN_FHIR_ID.uri }
    }

    protected fun verifyAllPresent(resources: List<Resource<*>>, expectedMap: Map<String, List<String>>) {
        assertEquals(resources.size, expectedMap.flatMap { it.value }.size)
        val found = resources.groupBy(
            { it.resourceType }, { it.getFhirIdentifier()?.value?.value }
        )

        expectedMap.forEach {
            val expectedFhirIDs = it.value
            val foundFhirIds = found[it.key]
            expectedFhirIDs.forEach { fhirId ->
                assertTrue(foundFhirIds?.contains(fhirId) == true)
            }
        }
    }

    @Deprecated("Use waitForMessage to give Mirth time to receive messages", ReplaceWith("waitForMessage(count, 1000)"))
    protected fun pause(time: Long = 1000) = runBlocking { delay(time) }
}
