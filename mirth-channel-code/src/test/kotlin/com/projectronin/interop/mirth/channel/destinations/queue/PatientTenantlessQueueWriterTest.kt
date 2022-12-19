package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.destinations.queue.PatientTenantlessQueueWriter
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

class PatientTenantlessQueueWriterTest {
    private val tenantId = "tenant"

    private lateinit var mockPublishService: PublishService
    private lateinit var writer: PatientTenantlessQueueWriter

    @BeforeEach
    fun setup() {
        mockPublishService = mockk()

        writer = PatientTenantlessQueueWriter(mockPublishService)
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - works`() {
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "Patient"
        |}
        """.trimMargin()
        val mockPatient = mockk<Patient>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(mockPatient)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { mockPublishService.publishFHIRResources(tenantId, any<List<Patient>>()) } returns true

        val response = writer.destinationWriter(
            "",
            mockSerialized,
            mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId),
            channelMap
        )
        assertEquals("Published 1 Patient(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
    }
}
