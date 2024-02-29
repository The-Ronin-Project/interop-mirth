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
import com.projectronin.interop.mirth.channel.base.kafka.completeness.KafkaDagPublisher
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class KafkaTopicReaderTest {
    private lateinit var kafkaLoadService: KafkaLoadService
    private lateinit var kafkaPublishService: KafkaPublishService
    private lateinit var tenantConfigService: TenantConfigurationService
    private lateinit var kafkaDagPublisher: KafkaDagPublisher
    private lateinit var channel: TestChannel
    private lateinit var mockMetadata: Metadata

    class TestChannel(
        kafkaPublishService: KafkaPublishService,
        kafkaLoadService: KafkaLoadService,
        override val tenantConfigService: TenantConfigurationService,
        kafkaDagPublisher: KafkaDagPublisher,
        override val maxEventBatchSize: Int = 20,
        override val publishEventOverrideBatchSize: Int = 20,
        override val publishEventOverrideResources: List<ResourceType> = emptyList(),
    ) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, mockk(), kafkaDagPublisher) {
        override val publishedResourcesSubscriptions = listOf(ResourceType.Patient, ResourceType.Practitioner)
        override val resource = ResourceType.Location
        override val destinations = emptyMap<String, KafkaEventResourcePublisher<Location>>()
        override val rootName = "test"
        override val channelGroupId = "test"
    }

    class LoadOnlyTestChannel(
        kafkaPublishService: KafkaPublishService,
        kafkaLoadService: KafkaLoadService,
        override val tenantConfigService: TenantConfigurationService,
    ) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, mockk(), mockk()) {
        override val publishedResourcesSubscriptions = emptyList<ResourceType>()
        override val resource = ResourceType.Location
        override val destinations = emptyMap<String, KafkaEventResourcePublisher<Location>>()
        override val rootName = "test"
        override val channelGroupId = "test"
    }

    class DateSpecificTestChannel(
        kafkaPublishService: KafkaPublishService,
        kafkaLoadService: KafkaLoadService,
        override val tenantConfigService: TenantConfigurationService,
        override val maxEventBatchSize: Int = 1,
    ) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, mockk(), mockk()) {
        override val publishedResourcesSubscriptions = listOf(ResourceType.Patient)
        override val resource = ResourceType.Location
        override val destinations = emptyMap<String, KafkaEventResourcePublisher<Location>>()
        override val rootName = "test"
        override val channelGroupId = "test"
        override val maxBackfillDays = 5
    }

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        kafkaPublishService = mockk()
        tenantConfigService = mockk()
        kafkaDagPublisher = mockk()
        channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        mockMetadata =
            mockk<Metadata> {
                every { runId } returns ">9000"
                every { targetedResources } returns emptyList()
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
        val mockEvent =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
            }
        val configDO =
            mockk<MirthTenantConfigDO> {
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
                "test",
            )
        } returns
            listOf(
                mockEvent,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(listOf(mockEvent)) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(">9000", message.dataMap[MirthKey.EVENT_RUN_ID.code])
        assertEquals(InteropResourcePublishV1::class.simpleName!!, message.dataMap[MirthKey.KAFKA_EVENT.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel filters out blocked resources from published nightly events`() {
        val mockEvent1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
            }
        val mockEvent2 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Appointment
                every { metadata } returns mockMetadata
            }
        val mockEvent3 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
            }
        val mockEvent4 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant4"
                every { resourceType } returns ResourceType.Appointment
                every { metadata } returns mockMetadata
            }
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "Location,Appointment" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        val configDO1 =
            mockk<MirthTenantConfigDO> {
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
                "test",
            )
        } returns
            listOf(
                mockEvent1,
                mockEvent2,
                mockEvent3,
                mockEvent4,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(listOf(mockEvent4)) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel filters out blocked resources from published nightly events - returns only published event messages`() {
        val mockEvent1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
            }
        val mockEvent2 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Appointment
                every { metadata } returns mockMetadata
            }
        val configDO =
            mockk<MirthTenantConfigDO> {
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
                "test",
            )
        } returns
            listOf(
                mockEvent1,
                mockEvent2,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Appointment, "test") } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.BACKFILL,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.BACKFILL,
                "test",
            )
        } returns emptyList()

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }

    @Test
    fun `channel filters out blocked resources from load events`() {
        val mockEvent1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
            }
        val configDO =
            mockk<MirthTenantConfigDO> {
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
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns listOf(mockEvent1)
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Appointment, "test") } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.BACKFILL,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.BACKFILL,
                "test",
            )
        } returns emptyList()

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }

    @Test
    fun `channel checks for all published nightly events`() {
        val mockEvent =
            mockk<InteropResourcePublishV1> {
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
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every { JacksonUtil.writeJsonValue(listOf(mockEvent)) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(InteropResourcePublishV1::class.simpleName!!, message.dataMap[MirthKey.KAFKA_EVENT.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel checks for load events after nightly events`() {
        val mockEvent =
            mockk<InteropResourceLoadV1> {
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
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test")
        } returns listOf(mockEvent)
        every { JacksonUtil.writeJsonValue(listOf(mockEvent)) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(InteropResourceLoadV1::class.simpleName!!, message.dataMap[MirthKey.KAFKA_EVENT.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel checks for ad hoc publish events after load events`() {
        val mockEvent =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
                every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.adhoc
            }
        val configDO =
            mockk<MirthTenantConfigDO> {
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
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(listOf(mockEvent)) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(InteropResourcePublishV1::class.simpleName!!, message.dataMap[MirthKey.KAFKA_EVENT.code])
        assertEquals("mockEvent", message.message)
    }

    @Test
    fun `channel checks for backfill events with ad-hoc`() {
        val mockEvent =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
                every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.backfill
            }
        val configDO =
            mockk<MirthTenantConfigDO> {
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
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns listOf(mockEvent)

        every { JacksonUtil.writeJsonValue(listOf(mockEvent)) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("mockTenant", message.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(InteropResourcePublishV1::class.simpleName!!, message.dataMap[MirthKey.KAFKA_EVENT.code])
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

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Practitioner, DataTrigger.BACKFILL, "test")
        } returns emptyList()

        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.BACKFILL, "test")
        } returns emptyList()

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }

    @Test
    fun `load event only channel works`() {
        val loadChannel = LoadOnlyTestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService)
        val mockEvent =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "mockTenant"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns mockMetadata
            }
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns listOf(mockEvent)
        every { JacksonUtil.writeJsonValue(listOf(mockEvent)) } returns "mockEvent"
        val messages = loadChannel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
    }

    @Test
    fun `backfill events are split out for date range`() {
        val splittingChannel = DateSpecificTestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService)
        val startDate =
            OffsetDateTime.of(
                LocalDate.of(2023, 9, 1),
                LocalTime.of(12, 0, 0),
                ZoneOffset.UTC,
            )
        val endDate =
            OffsetDateTime.of(
                LocalDate.of(2023, 9, 7),
                LocalTime.of(12, 0, 0),
                ZoneOffset.UTC,
            )
        // can't be mockk because we're gonna copy it
        val actualEvent =
            InteropResourcePublishV1(
                tenantId = "mockTenant",
                resourceJson = "{}",
                resourceType = ResourceType.Location,
                dataTrigger = InteropResourcePublishV1.DataTrigger.backfill,
                metadata =
                    Metadata(
                        runId = "1234",
                        runDateTime = OffsetDateTime.now(),
                        targetedResources = emptyList(),
                        backfillRequest =
                            Metadata.BackfillRequest(
                                backfillId = "123",
                                backfillStartDate = startDate,
                                backfillEndDate = endDate,
                            ),
                    ),
            )
        // we're checking the actual events produced, and we're not using a mock,
        // so we can let the actual serialization logic run
        unmockkObject(JacksonUtil)
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every {
            tenantConfigService.getConfiguration("mockTenant")
        } returns configDO
        every { kafkaLoadService.retrieveLoadEvents(any(), any()) } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                any(),
                match { it == DataTrigger.NIGHTLY },
                any(),
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(ResourceType.Patient, DataTrigger.AD_HOC, "test")
        } returns listOf(actualEvent)

        val messages = splittingChannel.channelSourceReader(emptyMap())
        assertEquals(2, messages.size)
        val events = messages.map { JacksonUtil.readJsonList(it.message, InteropResourcePublishV1::class) }.flatten()
        assertEquals("123", events[0].metadata.backfillRequest?.backfillId)
        assertEquals("123", events[1].metadata.backfillRequest?.backfillId)
        assertEquals(startDate, events[0].metadata.backfillRequest?.backfillStartDate)
        assertEquals(startDate.plusDays(5), events[1].metadata.backfillRequest?.backfillStartDate)
        assertEquals(startDate.plusDays(5), events[0].metadata.backfillRequest?.backfillEndDate)
        assertEquals(endDate, events[1].metadata.backfillRequest?.backfillEndDate)
        assertEquals("mockTenant", events[0].tenantId)
        assertEquals("mockTenant", events[1].tenantId)
    }

    @Test
    fun `publish events are batched into messages by tenant, run id and resource type`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }
        val metadata2 =
            mockk<Metadata> {
                every { runId } returns "run2"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEvent2Tenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEventTenant2Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant2"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEventTenant1Run2 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata2
            }
        val practitionerEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Practitioner
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEventTenant1Run2,
                patientEventTenant2Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns listOf(practitionerEventTenant1Run1)

        every {
            JacksonUtil.writeJsonValue(
                listOf(
                    patientEventTenant1Run1,
                    patientEvent2Tenant1Run1,
                ),
            )
        } returns "tenant1-run1-patient"
        every { JacksonUtil.writeJsonValue(listOf(patientEventTenant2Run1)) } returns "tenant2-run1-patient"
        every { JacksonUtil.writeJsonValue(listOf(practitionerEventTenant1Run1)) } returns "tenant1-run1-practitioner"
        every { JacksonUtil.writeJsonValue(listOf(patientEventTenant1Run2)) } returns "tenant1-run2-patient"

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(4, messages.size)

        val messagesByResponse = messages.associateBy { it.message }

        val message1 = messagesByResponse["tenant1-run1-patient"]!!
        assertEquals("tenant1", message1.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("run1", message1.dataMap[MirthKey.EVENT_RUN_ID.code])

        val message2 = messagesByResponse["tenant2-run1-patient"]!!
        assertEquals("tenant2", message2.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("run1", message2.dataMap[MirthKey.EVENT_RUN_ID.code])

        val message3 = messagesByResponse["tenant1-run1-practitioner"]!!
        assertEquals("tenant1", message3.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("run1", message3.dataMap[MirthKey.EVENT_RUN_ID.code])

        val message4 = messagesByResponse["tenant1-run2-patient"]!!
        assertEquals("tenant1", message4.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("run2", message4.dataMap[MirthKey.EVENT_RUN_ID.code])
    }

    @Test
    fun `publish events honor max batch size`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEvent2Tenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEvent3Tenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher, 2)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(2, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `publish events honor batch size override with matching resource type`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEvent2Tenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEvent3Tenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            TestChannel(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                kafkaDagPublisher,
                3,
                2,
                listOf(ResourceType.Patient),
            )
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(2, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `publish events after checking blocked resource`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "Encounter" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `publish events get both targeted and blocked resource, location is blocked, zero returned`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "Location" // unlikely to happen, but for fun
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns listOf("Observation")
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `publish events get both targeted and blocked resource, location is targeted, one returned`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns ""
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns listOf("Location")
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `publish events get both targeted and blocked resource, both are empty, one returned`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // unlikely to happen, but for fun
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `load events get after checking blocked resource`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "Encounter" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns
            listOf(
                patientEventTenant1Run1,
            )

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `load events get both targeted and blocked resource, location is blocked, zero returned`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "Location" // unlikely to happen, but for fun
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns listOf("Observation")
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns
            listOf(
                patientEventTenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `load events get both targeted and blocked resource, location is targeted, one returned`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns ""
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns listOf("Location")
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns
            listOf(
                patientEventTenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `load events get both targeted and blocked resource, both are empty, one returned`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // unlikely to happen, but for fun
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()
        every { kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test") } returns
            listOf(
                patientEventTenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.AD_HOC,
                "test",
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `publish events ignore batch size override with no matching resource type`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }

        val patientEventTenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEvent2Tenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        val patientEvent3Tenant1Run1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Patient
                every { metadata } returns metadata1
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            TestChannel(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                kafkaDagPublisher,
                1,
                2,
                listOf(ResourceType.Practitioner),
            )
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(3, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `load events are batched into messages by tenant and run id`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }
        val metadata2 =
            mockk<Metadata> {
                every { runId } returns "run2"
                every { targetedResources } returns emptyList()
            }

        val locationEventTenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }
        val locationEvent2Tenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }
        val locationEventTenant1Run2 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata2
            }
        val locationEventTenant2Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant2"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }

        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
                false,
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
                false,
            )
        } returns emptyList()
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test", false)
        } returns
            listOf(
                locationEventTenant1Run1,
                locationEvent2Tenant1Run1,
                locationEventTenant1Run2,
                locationEventTenant2Run1,
            )

        every {
            JacksonUtil.writeJsonValue(
                listOf(
                    locationEventTenant1Run1,
                    locationEvent2Tenant1Run1,
                ),
            )
        } returns "tenant1-run1-location"
        every { JacksonUtil.writeJsonValue(listOf(locationEventTenant2Run1)) } returns "tenant2-run1-location"
        every { JacksonUtil.writeJsonValue(listOf(locationEventTenant1Run2)) } returns "tenant1-run2-location"

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(3, messages.size)

        val messagesByResponse = messages.associateBy { it.message }

        val message1 = messagesByResponse["tenant1-run1-location"]!!
        assertEquals("tenant1", message1.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("run1", message1.dataMap[MirthKey.EVENT_RUN_ID.code])

        val message2 = messagesByResponse["tenant2-run1-location"]!!
        assertEquals("tenant2", message2.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("run1", message2.dataMap[MirthKey.EVENT_RUN_ID.code])

        val message3 = messagesByResponse["tenant1-run2-location"]!!
        assertEquals("tenant1", message3.dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("run2", message3.dataMap[MirthKey.EVENT_RUN_ID.code])
    }

    @Test
    fun `load events honor max batch size`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }
        val locationEventTenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }
        val locationEvent2Tenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }
        val locationEvent3Tenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }

        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
                false,
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
                false,
            )
        } returns emptyList()
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test", false)
        } returns
            listOf(
                locationEventTenant1Run1,
                locationEvent2Tenant1Run1,
                locationEvent3Tenant1Run1,
            )

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel = TestChannel(kafkaPublishService, kafkaLoadService, tenantConfigService, kafkaDagPublisher, 2)
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(2, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `load events ignore max batch size override`() {
        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns emptyList()
            }
        val locationEventTenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }
        val locationEvent2Tenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }
        val locationEvent3Tenant1Run1 =
            mockk<InteropResourceLoadV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Location
                every { metadata } returns metadata1
            }

        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "test",
                false,
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Practitioner,
                DataTrigger.NIGHTLY,
                "test",
                false,
            )
        } returns emptyList()
        every {
            kafkaLoadService.retrieveLoadEvents(ResourceType.Location, "test", false)
        } returns
            listOf(
                locationEventTenant1Run1,
                locationEvent2Tenant1Run1,
                locationEvent3Tenant1Run1,
            )

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            TestChannel(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                kafkaDagPublisher,
                2,
                1,
            )
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(2, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }
}
