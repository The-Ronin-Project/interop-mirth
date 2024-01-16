package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channels.client.AidboxClient
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.mirth.ChannelMap
import com.projectronin.interop.mirth.channels.client.mirth.Message
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

/**
 * Base class to handle testing an individual channel
 * takes a [channelName] to identify the channel,
 * [aidboxResourceTypes] a list of resource types to clear in aidbox and [mockEHRResourceTypes]
 * a list of resources  types to clear in mockERH
 */

abstract class BaseChannelTest(
    private val channelName: String,
    private val aidboxResourceTypes: List<String>,
    private val mockEHRResourceTypes: List<String> = emptyList(),
) {
    var tenantInUse = "NOTSET"
    protected val testChannelId = ChannelMap.installedDag[channelName]!!
    protected val logger = KotlinLogging.logger(this::class.java.name)

    @BeforeEach
    fun setup() {
        logger.info { "Starting test" }
        logger.debug { "BeforeEach start" }
        clearMessages()
        deleteAidboxResources(*aidboxResourceTypes.toTypedArray())
        deleteMockEHRResources(*mockEHRResourceTypes.toTypedArray())
        MockOCIServerClient.client.clear("PutObjectExpectation")
        MockOCIServerClient.resetRawPublish()
        logger.debug { "BeforeEach end" }
    }

    @AfterEach
    fun tearDown() {
        logger.info { "Finishing test" }
        logger.debug { "AfterEach start" }
        MockEHRTestData.purge()
        AidboxTestData.purge()
        logger.debug { "AfterEach end" }
    }

    companion object {
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

    /**
     * Clears the messages and statistics for this channel. If the channel is currently running, it will be restarted.
     */
    protected fun clearMessages() {
        MirthClient.clearChannelMessages(testChannelId)
    }

    protected fun startChannel(
        waitForMessage: Boolean = false,
        channelToDeploy: String = testChannelId,
    ) {
        MirthClient.startChannel(channelToDeploy)

        if (waitForMessage) {
            waitForMessage(1)
        }
    }

    protected fun getChannelMessageIds(): List<Message> {
        return MirthClient.getChannelMessageIds(testChannelId)
    }

    protected fun getAidboxResourceCount(
        resourceType: String,
        tenant: String = tenantInUse,
    ): Int {
        val resources = AidboxClient.getAllResourcesForTenant(resourceType, tenant)
        return resources.get("total").asInt()
    }

    private fun deleteAidboxResources(vararg resourceTypes: String) {
        resourceTypes.forEach {
            tenantsToTest().forEach { tenant ->
                AidboxClient.deleteAllResources(it, tenant)
            }
        }
    }

    private fun deleteMockEHRResources(vararg resourceTypes: String) {
        resourceTypes.forEach {
            MockEHRClient.deleteAllResources(it)
        }
    }

    protected fun assertAllConnectorsStatus(
        messageList: List<Message>,
        status: MirthResponseStatus = MirthResponseStatus.SENT,
    ) {
        messageList.forEach { connectorMessage ->
            connectorMessage.destinationMessages.forEach {
                assertEquals(
                    status.name,
                    it.status,
                    "status for connector ${it.connectorName} was not ${status.name}. Actual status: ${it.status}",
                )
            }
        }
    }

    protected fun waitForMessage(
        minimumCount: Int,
        timeout: Int = 20,
        channelID: String = testChannelId,
    ) {
        runBlocking {
            withTimeout(timeout = timeout.seconds) {
                MirthClient.waitForMessage(minimumCount, channelID)
            }
        }
    }
}
