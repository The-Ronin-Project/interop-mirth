package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.kafka.model.PublishResourceWrapper
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.channel.util.serialize
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppointmentTenantlessQueueWriterTest {
    private val tenantId = "tenant"
    private val mockTenant = mockk<Tenant>()
    private lateinit var mockTenantService: TenantService
    private lateinit var mockPublishService: PublishService
    private lateinit var writer: AppointmentTenantlessQueueWriter

    @BeforeEach
    fun setup() {
        mockPublishService = mockk()
        mockTenantService =
            mockk {
                every { getTenantForMnemonic(tenantId) } returns mockTenant
            }
        writer = AppointmentTenantlessQueueWriter(mockPublishService)
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - works`() {
        val mockSerialized =
            """{
        |  "id": "12345",
        |  "resourceType": "Appointment",
        |  "status": "arrived",
        |  "subject": {
        |    "reference": "Patient/1234"
        |  }
        |}
            """.trimMargin()
        val mockAppointment = mockk<Appointment>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(mockAppointment)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        val metadata = generateMetadata()
        every {
            mockPublishService.publishResourceWrappers(
                tenantId,
                any<List<PublishResourceWrapper>>(),
                metadata,
            )
        } returns
            mockk {
                every { isSuccess } returns true
            }

        val response =
            writer.destinationWriter(
                "",
                mockSerialized,
                mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId, MirthKey.EVENT_METADATA.code to serialize(metadata)),
                channelMap,
            )
        assertEquals("Published 1 Appointment(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
    }
}
