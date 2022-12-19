package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.destinations.queue.PractitionerQueueWriter
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

class PractitionerQueueWriterTest {
    private val tenantId = "tenant"

    private lateinit var mockPublishService: PublishService
    private lateinit var writer: PractitionerQueueWriter

    @BeforeEach
    fun setup() {
        mockPublishService = mockk()

        val tenantService = mockk<TenantService>()
        val transformManager = mockk<TransformManager>()
        writer = PractitionerQueueWriter(tenantService, transformManager, mockPublishService)
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - works`() {
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "Practitioner"
        |}
        """.trimMargin()
        val mockPractitioner = mockk<Practitioner>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(mockPractitioner)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { mockPublishService.publishFHIRResources(tenantId, any<List<Practitioner>>()) } returns true

        val response = writer.channelDestinationWriter(
            tenantId,
            mockSerialized,
            emptyMap(),
            channelMap
        )
        assertEquals("Published 1 Practitioner(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
    }
}
