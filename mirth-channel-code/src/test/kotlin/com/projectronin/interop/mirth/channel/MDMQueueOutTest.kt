package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.TenantConfigurationFactory
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.HL7Message
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MDMQueueOutTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "tenant"
    }
    private lateinit var mockTenantConfigurationFactory: TenantConfigurationFactory
    private lateinit var mockQueueService: QueueService
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: MDMQueueOut

    @BeforeEach
    fun setup() {
        mockTenantConfigurationFactory = mockk()
        mockQueueService = mockk()
        mockServiceFactory = mockk {
            every { tenantConfigurationFactory() } returns mockTenantConfigurationFactory
            every { queueService() } returns mockQueueService
        }
        channel = MDMQueueOut(mockServiceFactory)
    }

    @Test
    fun `onDeploy - works`() {
        every { mockTenantConfigurationFactory.getMDMInfo("tenant") } returns Pair("address", 1)
        val serviceMap = channel.channelOnDeploy(mockTenant.mnemonic, emptyMap())
        assertEquals(2, serviceMap.size)
        assertEquals("address", serviceMap["ADDRESS"])
        assertEquals("1", serviceMap["PORT"])
    }

    @Test
    fun `onDeploy - works with bad values`() {
        every { mockTenantConfigurationFactory.getMDMInfo("tenant") } returns null
        val serviceMap = channel.channelOnDeploy(mockTenant.mnemonic, emptyMap())
        assertEquals(2, serviceMap.size)
        assertEquals("null", serviceMap["ADDRESS"])
        assertEquals("null", serviceMap["PORT"])
    }

    @Test
    fun `sourceReader - works`() {
        val mockHL7 = "MSH|^~\\&|RONIN|RONIN||HOSPITAL|20200524130600||MDM^T02|1|P|2.5.1|||NE||\n" +
            "EVN|T02|20200524130600|"
        val mockMessage = mockk<HL7Message> {
            every { text } returns mockHL7
        }
        every { mockQueueService.dequeueHL7Messages("tenant", MessageType.MDM, null, 5) } returns listOf(mockMessage)
        val mirthMessageList = channel.channelSourceReader(mockTenant.mnemonic, emptyMap())
        assertEquals(1, mirthMessageList.size)
        assertEquals(mockHL7, mirthMessageList.first().message)
    }
}
