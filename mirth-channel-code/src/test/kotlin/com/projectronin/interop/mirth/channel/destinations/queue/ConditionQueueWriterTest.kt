package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.destinations.queue.ConditionQueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConditionQueueWriterTest {
    private val tenantId = "tenant"

    private lateinit var mockPublishService: PublishService
    private lateinit var writer: ConditionQueueWriter

    @BeforeEach
    fun setup() {
        mockPublishService = mockk()

        val tenantService = mockk<TenantService>()
        val transformManager = mockk<TransformManager>()
        writer = ConditionQueueWriter(tenantService, transformManager, mockPublishService)
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - works`() {
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "Condition",
        |  "subject": {
        |    "reference": "Patient/1234"
        |  }
        |}
        """.trimMargin()
        val mockCondition = mockk<Condition>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(mockCondition)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { mockPublishService.publishFHIRResources(tenantId, any<List<Condition>>()) } returns true

        val response = writer.channelDestinationWriter(
            tenantId,
            mockSerialized,
            emptyMap(),
            channelMap
        )
        assertEquals("Published 1 Condition(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
    }
}
