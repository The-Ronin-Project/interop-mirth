package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.mirth.channel.destinations.TestResource
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.publishers.PublishService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TenantlessQueueWriterTest {
    private val tenantId = "tenant"

    private lateinit var mockPublishService: PublishService
    private lateinit var writer: TenantlessQueueWriter<TestResource>

    @BeforeEach
    fun setup() {
        mockPublishService = mockk()
        writer = object : TenantlessQueueWriter<TestResource>(mockPublishService, TestResource::class) {}
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - works`() {
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "TestResource"
        |}
        """.trimMargin()
        val mockRoninDomainResource = mockk<TestResource>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(mockRoninDomainResource)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { mockPublishService.publishFHIRResources(tenantId, any<List<TestResource>>()) } returns true

        val response = writer.destinationWriter(
            "name",
            mockSerialized,
            mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId),
            channelMap
        )
        assertEquals("Published 1 TestResource(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
    }

    @Test
    fun `destination transformer`() {
        val message = writer.destinationTransformer("name", "message", emptyMap(), emptyMap())
        assertEquals("message", message.message)
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "TestResource"
        |}
        """.trimMargin()
        val mockRoninDomainResource = mockk<TestResource>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any<TestResource>()) } returns mockSerialized

        val resourceList = listOf(mockRoninDomainResource)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { mockPublishService.publishFHIRResources(tenantId, any<List<TestResource>>()) } returns false

        val response = writer.destinationWriter(
            tenantId,
            mockSerialized,
            mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId),
            channelMap
        )
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
        assertEquals("Failed to publish TestResource(s)", response.message)
    }
}
