package com.projectronin.interop.mirth.channel.base.kafka

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
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
    private lateinit var tenantConfigService: TenantConfigurationService
    private lateinit var channel: TestChannel
    private lateinit var mockMetadata: Metadata

    class TestChannel(
        kafkaPublishService: KafkaPublishService,
        kafkaLoadService: KafkaLoadService,
        override val tenantConfigService: TenantConfigurationService
    ) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, mockk()) {
        override val publishedResourcesSubscriptions = listOf(ResourceType.Patient, ResourceType.Practitioner)
        override val resource = ResourceType.Location
        override val destinations = emptyMap<String, KafkaEventResourcePublisher<Location>>()
        override val rootName = "test"
        override val channelGroupId = "test"
    }

    class LoadOnlyTestChannel(
        kafkaPublishService: KafkaPublishService,
        kafkaLoadService: KafkaLoadService,
        override val tenantConfigService: TenantConfigurationService
    ) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, mockk()) {
        override val publishedResourcesSubscriptions = emptyList<ResourceType>()
        override val resource = ResourceType.Location
        override val destinations = emptyMap<String, KafkaEventResourcePublisher<Location>>()
        override val rootName = "test"
        override val channelGroupId = "test"
    }

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        kafkaPublishService = mockk()
        tenantConfigService = mockk()
        channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService)
        mockMetadata = mockk<Metadata> {
            every { runId } returns ">9000"
        }

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
            every { resourceType } returns ResourceType.Location
            every { metadata } returns mockMetadata
        }
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test"
            )
        } returns listOf(
            mockEvent
        )
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(">9000", message.dataMap[MirthKey.EVENT_RUN_ID.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel filters out blocked resources from published nightly events`() {
        val mockEvent1 = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Location
            every { metadata } returns mockMetadata
        }
        val mockEvent2 = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Appointment
            every { metadata } returns mockMetadata
        }
        val mockEvent3 = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Location
            every { metadata } returns mockMetadata
        }
        val mockEvent4 = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant4"
            every { resourceType } returns ResourceType.Appointment
            every { metadata } returns mockMetadata
        }
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "Location,Appointment" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        val configDO1 = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "Patient" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every {
            tenantConfigService.getConfiguration("mockTenant4")
        } returns configDO1
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test"
            )
        } returns listOf(
            mockEvent1,
            mockEvent2,
            mockEvent3,
            mockEvent4
        )
        every { JacksonUtil.writeJsonValue(mockEvent4) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel filters out blocked resources from published nightly events - returns only published event messages`() {
        val mockEvent1 = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Location
        }
        val mockEvent2 = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Appointment
        }
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "Location,Appointment" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test"
            )
        } returns listOf(
            mockEvent1,
            mockEvent2
        )
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Appointment, "test") } returns emptyList()
        every { kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.AD_HOC, "test") } returns emptyList()
        every { kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.AD_HOC, "test") } returns emptyList()

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }

    @Test
    fun `channel filters out blocked resources from load events`() {
        val mockEvent1 = mockk<InteropResourceLoadV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Location
        }
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "Location" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test"
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test"
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns listOf(mockEvent1)
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Appointment, "test") } returns emptyList()
        every { kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.AD_HOC, "test") } returns emptyList()
        every { kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.AD_HOC, "test") } returns emptyList()

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }

    @Test
    fun `channel checks for all published nightly events`() {
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Location
            every { metadata } returns mockMetadata
        }
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.NIGHTLY, "test")
        } returns listOf(mockEvent)
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
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
            every { resourceType } returns ResourceType.Location
            every { metadata } returns mockMetadata
        }
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test")
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
            every { resourceType } returns ResourceType.Location
            every { metadata } returns mockMetadata
        }
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.NIGHTLY, "test")
        } returns emptyList()
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test")
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.AD_HOC, "test")
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
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.NIGHTLY, "test")
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.NIGHTLY, "test")
        } returns emptyList()

        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test")
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.AD_HOC, "test")
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.AD_HOC, "test")
        } returns emptyList()

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }

    @Test
    fun `load event only channel works`() {
        val loadChannel = LoadOnlyTestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService)
        val mockEvent = mockk<InteropResourceLoadV1> {
            every { tenantId } returns "mockTenant"
            every { resourceType } returns ResourceType.Location
            every { metadata } returns mockMetadata
        }
        val configDO = mockk<MirthTenantConfigDO> {
            every { blockedResources } returns "" // should this be the actual resource name
            every { locationIds } returns "12345678"
        }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns listOf(mockEvent)
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"
        val messages = loadChannel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
    }
}
