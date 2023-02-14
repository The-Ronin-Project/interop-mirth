package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.destinations.AppointmentPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppointmentLoadTest {
    private lateinit var kafkaLoadService: KafkaLoadService
    private lateinit var kafkaPublishService: KafkaPublishService
    private lateinit var channel: AppointmentLoad

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        kafkaPublishService = mockk()
        mockkObject(JacksonUtil)

        val appointmentWriter = mockk<AppointmentPublish>()
        channel = AppointmentLoad(kafkaPublishService, kafkaLoadService, appointmentWriter)
    }

    @Test
    fun `channel creation works`() {
        assertEquals("AppointmentLoad", channel.rootName)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `appointment sourceReader works`() {
        val mockAppointmentEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenantId"
        }
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY)
        } returns listOf(mockAppointmentEvent)
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.AD_HOC)
        } throws Exception("nothing to see here")
        every {
            JacksonUtil.writeJsonValue(mockAppointmentEvent)
        } returns "mockAppointmentEvent"

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val firstMessage = messages.first()
        assertEquals("mockTenantId", firstMessage.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockAppointmentEvent", firstMessage.message)
    }

    @Test
    fun `appointment sourceReader reads from ad-hoc if nightly and loads returns nothing`() {
        val mockAppointmentEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenantId"
        }
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY) } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.APPOINTMENT) } returns emptyList()
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.AD_HOC) } returns listOf(
            mockAppointmentEvent
        )
        every { JacksonUtil.writeJsonValue(mockAppointmentEvent) } returns "mockAppointmentEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val firstMessage = messages.first()
        assertEquals("mockTenantId", firstMessage.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockAppointmentEvent", firstMessage.message)
    }

    @Test
    fun `appointment sourceReader reads from loads if no nightly returned`() {
        val mockAppointmentEvent = mockk<InteropResourceLoadV1> {
            every { tenantId } returns "mockTenantId"
        }
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY) } returns emptyList()
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.APPOINTMENT)
        } returns listOf(
            mockAppointmentEvent
        )
        every { JacksonUtil.writeJsonValue(mockAppointmentEvent) } returns "mockAppointmentEvent"

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val firstMessage = messages.first()
        assertEquals("mockTenantId", firstMessage.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockAppointmentEvent", firstMessage.message)
    }
}
