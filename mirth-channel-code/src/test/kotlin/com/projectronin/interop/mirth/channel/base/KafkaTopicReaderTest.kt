package com.projectronin.interop.mirth.channel.base

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.enums.MirthKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KafkaTopicReaderTest {
    private lateinit var kafkaLoadService: KafkaLoadService
    private lateinit var kafkaPublishService: KafkaPublishService
    private lateinit var channel: TestChannel

    class TestChannel(kafkaPublishService: KafkaPublishService, kafkaLoadService: KafkaLoadService) :
        KafkaTopicReader(kafkaPublishService, kafkaLoadService, mockk()) {
        override val publishedResourcesSubscriptions = listOf(ResourceType.PATIENT, ResourceType.PRACTITIONER)
        override val resource = ResourceType.LOCATION
        override val destinations = emptyMap<String, KafkaEventResourcePublisher<Location>>()
        override val rootName = "test"
        override val channelGroupId = "test"
    }
    class LoadOnlyTestChannel(kafkaPublishService: KafkaPublishService, kafkaLoadService: KafkaLoadService) :
        KafkaTopicReader(kafkaPublishService, kafkaLoadService, mockk()) {
        override val publishedResourcesSubscriptions = emptyList<ResourceType>()
        override val resource = ResourceType.LOCATION
        override val destinations = emptyMap<String, KafkaEventResourcePublisher<Location>>()
        override val rootName = "test"
        override val channelGroupId = "test"
    }

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        kafkaPublishService = mockk()
        channel = TestChannel(kafkaPublishService, kafkaLoadService)
        mockkObject(JacksonUtil)
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @Test
    fun `channel creation works`() {
        assertNotNull(channel.destinations)
        assertNotNull(channel.publishedResourcesSubscriptions)
        assertNotNull(channel.resource)
        assertNotNull(channel.destinations)
    }

    @Test
    fun `channel first reads from published nightly events`() {
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
        }
        every { kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY, "test") } returns listOf(
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
    fun `channel checks for all published nightly events`() {
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
        }
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PRACTITIONER, DataTrigger.NIGHTLY, "test")
        } returns listOf(mockEvent)
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel checks for load events after nightly events`() {
        val mockEvent = mockk<InteropResourceLoadV1> {
            every { tenantId } returns "mockTenant"
        }
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PRACTITIONER, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.LOCATION, "test")
        } returns listOf(mockEvent)
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel checks for ad hoc publish events after load events`() {
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
        }
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PRACTITIONER, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.LOCATION, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.AD_HOC, "test")
        } returns listOf(mockEvent)

        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel returns nothing if no events`() {
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY, "test")
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PRACTITIONER, DataTrigger.NIGHTLY, "test")
        } returns emptyList()

        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.LOCATION, "test")
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.AD_HOC, "test")
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.PRACTITIONER, DataTrigger.AD_HOC, "test")
        } returns emptyList()

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }

    @Test
    fun `load event only channel works`() {
        val loadChannel = LoadOnlyTestChannel(kafkaPublishService, kafkaLoadService)
        val mockEvent = mockk<InteropResourceLoadV1> {
            every { tenantId } returns "mockTenant"
        }
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.LOCATION, "test") } returns listOf(mockEvent)
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"
        val messages = loadChannel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
    }
}
