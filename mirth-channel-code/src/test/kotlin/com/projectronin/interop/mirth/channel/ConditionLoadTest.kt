package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.destinations.ConditionPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConditionLoadTest {
    private lateinit var kafkaLoadService: KafkaLoadService
    private lateinit var kafkaPublishService: KafkaPublishService
    private lateinit var channel: ConditionLoad

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        kafkaPublishService = mockk()
        mockkObject(JacksonUtil)

        val conditionWriter = mockk<ConditionPublish>()
        channel =
            ConditionLoad(kafkaPublishService, kafkaLoadService, conditionWriter)
    }

    @Test
    fun `create channel - works`() {
        assertEquals("ConditionLoad", channel.rootName)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `sourceReader works`() {
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
        }
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY) } returns listOf(
            mockEvent
        )
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.AD_HOC)
        } throws Exception("This shouldn't actually get hit")
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `sourceReader only reads from ad-hoc if nightly and loads returns nothing`() {
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
        }
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY) } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.CONDITION) } returns emptyList()
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.AD_HOC) } returns listOf(
            mockEvent
        )
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `sourceReader only reads from loads if no nightly`() {
        val mockEvent = mockk<InteropResourceLoadV1> {
            every { tenantId } returns "mockTenant"
        }
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY) } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.CONDITION) } returns listOf(
            mockEvent
        )
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockEvent", message.message)
    }
}
