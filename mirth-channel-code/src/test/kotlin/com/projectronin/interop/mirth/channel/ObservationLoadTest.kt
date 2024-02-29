package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.conditionStage
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.interop.mirth.channel.base.kafka.completeness.KafkaDagPublisher
import com.projectronin.interop.mirth.channel.destinations.ObservationPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObservationLoadTest {
    private lateinit var channel: ObservationLoad

    @BeforeEach
    fun setup() {
        channel = ObservationLoad(mockk(), mockk(), mockk(), mockk(), mockk())
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @Test
    fun `create channel - works`() {
        assertEquals("ObservationLoad", channel.rootName)
        assertEquals("interop-mirth-observation_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
        assertEquals(30, channel.maxBackfillDays)
    }

    @Test
    fun `channel deploy publishes DAG`() {
        val kafkaDagPublisher: KafkaDagPublisher =
            mockk {
                every { publishDag(any(), any()) } returns PushResponse()
            }
        val channel =
            ObservationLoad(
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                kafkaDagPublisher,
            )
        channel.onDeploy(channel.rootName, emptyMap())

        verify {
            kafkaDagPublisher.publishDag(
                withArg { resourceType ->
                    assertEquals(ResourceType.Observation, resourceType)
                },
                withArg { consumedResources ->
                    assertEquals(consumedResources.size, 2)
                    Assertions.assertTrue(consumedResources.contains(ResourceType.Patient))
                    Assertions.assertTrue(consumedResources.contains(ResourceType.Condition))
                },
            )
        }
    }

    @Test
    fun `publish events honor batch size override with matching resource type`() {
        val kafkaLoadService: KafkaLoadService = mockk()
        val kafkaPublishService: KafkaPublishService = mockk()
        val tenantConfigService: TenantConfigurationService = mockk()
        val observationPublish: ObservationPublish = mockk()
        val kafkaDagPublisher: KafkaDagPublisher = mockk()

        mockkObject(JacksonUtil)

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
                "interop-mirth-observation_group",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Condition,
                DataTrigger.NIGHTLY,
                "interop-mirth-observation_group",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            ObservationLoad(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                observationPublish,
                kafkaDagPublisher,
            )

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(3, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `conditions published are not filtered when there are relevant observation reference`() {
        val kafkaLoadService: KafkaLoadService = mockk()
        val kafkaPublishService: KafkaPublishService = mockk()
        val tenantConfigService: TenantConfigurationService = mockk()
        val observationPublish: ObservationPublish = mockk()
        val kafkaDagPublisher: KafkaDagPublisher = mockk()

        mockkObject(JacksonUtil)

        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns null
            }

        val conditionEvent1 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Condition
                every { metadata } returns metadata1
                every { resourceJson } returns "conditionResource"
            }
        val conditionEvent2 =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Condition
                every { metadata } returns metadata1
                every { resourceJson } returns "fail"
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "interop-mirth-observation_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Condition,
                DataTrigger.NIGHTLY,
                "interop-mirth-observation_group",
            )
        } returns listOf(conditionEvent1, conditionEvent2)

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        every { JacksonUtil.readJsonObject("conditionResource", Condition::class) } returns
            condition {
                stage of
                    listOf(
                        conditionStage {
                            assessment of
                                listOf(
                                    reference("Observation", "123"),
                                )
                        },
                    )
            }

        val channel =
            ObservationLoad(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                observationPublish,
                kafkaDagPublisher,
            )

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }

    @Test
    fun `conditions published are filtered when there are  no relevant observation reference`() {
        val kafkaLoadService: KafkaLoadService = mockk()
        val kafkaPublishService: KafkaPublishService = mockk()
        val tenantConfigService: TenantConfigurationService = mockk()
        val observationPublish: ObservationPublish = mockk()
        val kafkaDagPublisher: KafkaDagPublisher = mockk()

        mockkObject(JacksonUtil)

        val configDO =
            mockk<MirthTenantConfigDO> {
                every { blockedResources } returns "" // should this be the actual resource name
                every { locationIds } returns "12345678"
            }
        every { tenantConfigService.getConfiguration(any()) } returns configDO

        val metadata1 =
            mockk<Metadata> {
                every { runId } returns "run1"
                every { targetedResources } returns null
            }

        val conditionEvent =
            mockk<InteropResourcePublishV1> {
                every { tenantId } returns "tenant1"
                every { resourceType } returns ResourceType.Condition
                every { metadata } returns metadata1
                every { resourceJson } returns "conditionResource"
            }
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Patient,
                DataTrigger.NIGHTLY,
                "interop-mirth-observation_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Condition,
                DataTrigger.NIGHTLY,
                "interop-mirth-observation_group",
            )
        } returns listOf(conditionEvent)
        every { kafkaLoadService.retrieveLoadEvents(any(), any(), any()) } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                any(),
                DataTrigger.AD_HOC,
                any(),
            )
        } returns emptyList()
        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        every { JacksonUtil.readJsonObject("conditionResource", Condition::class) } returns
            condition {
                stage of
                    listOf(
                        conditionStage {
                            assessment of
                                emptyList()
                        },
                    )
            }

        val channel =
            ObservationLoad(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                observationPublish,
                kafkaDagPublisher,
            )

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(0, messages.size)
    }
}
