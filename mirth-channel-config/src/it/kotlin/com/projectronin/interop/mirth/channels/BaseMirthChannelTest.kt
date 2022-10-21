package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.AidboxClient
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.io.StringWriter
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
abstract class BaseMirthChannelTest(
    private val channelName: String,
    private val aidboxResourceTypes: List<String>,
    private val mockEHRResourceTypes: List<String> = emptyList()
) {
    private val testChannelName = "$testTenant-$channelName"
    protected val testChannelId = installChannel()

    @BeforeEach
    fun setup() {
        clearMessages()
        deleteAidboxResources(*aidboxResourceTypes.toTypedArray())
        deleteMockEHRResources(*mockEHRResourceTypes.toTypedArray())
    }

    @AfterEach
    fun tearDown() {
        MockEHRTestData.purge()
        AidboxTestData.purge()
        stopChannel()
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

    /**
     * Clears the messages and statistics for this channel. If the channel is currently running, it will be restarted.
     */
    protected fun clearMessages() {
        MirthClient.clearAllStatistics()
        MirthClient.clearChannelMessages(testChannelId)
    }

    protected fun deployAndStartChannel(waitForMessage: Boolean, timeout: Int = 60) {
        MirthClient.deployChannel(testChannelId)
        MirthClient.startChannel(testChannelId)

        if (waitForMessage) {
            waitForMessage(1, timeout)
        }
    }

    protected fun stopChannel() {
        MirthClient.stopChannel(testChannelId)
    }

    protected fun getAidboxResourceCount(resourceType: String): Int {
        val resources = AidboxClient.getAllResourcesForTenant(resourceType, testTenant)
        return resources.get("total").asInt()
    }

    protected fun getMockEHRResourceCount(resourceType: String): Int {
        val resources = MockEHRClient.getAllResources(resourceType)
        return resources.get("total").asInt()
    }

    protected fun deleteAidboxResources(vararg resourceTypes: String) {
        resourceTypes.forEach {
            AidboxClient.deleteAllResources(it, testTenant)
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
                    "status for connector ${it.connectorName} was not SENT. Actual status: $${it.connectorName}"
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

    protected fun pause() = runBlocking { delay(1000) }
}
