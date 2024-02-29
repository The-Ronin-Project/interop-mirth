package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.interop.mirth.channel.base.kafka.completeness.KafkaDagPublisher
import com.projectronin.interop.mirth.channel.destinations.DiagnosticReportPublish
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

class DiagnosticReportLoadTest {
    private lateinit var channel: DiagnosticReportLoad

    @BeforeEach
    fun setup() {
        channel = DiagnosticReportLoad(mockk(), mockk(), mockk(), mockk(), mockk())
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @Test
    fun `create channel - works`() {
        assertEquals("DiagnosticReportLoad", channel.rootName)
        assertEquals("interop-mirth-diagnostic-report_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `channel deploy publishes DAG`() {
        val kafkaDagPublisher: KafkaDagPublisher =
            mockk {
                every { publishDag(any(), any()) } returns PushResponse()
            }
        val channel =
            DiagnosticReportLoad(
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
                    assertEquals(ResourceType.DiagnosticReport, resourceType)
                },
                withArg { consumedResources ->
                    assertEquals(consumedResources.size, 1)
                    Assertions.assertTrue(consumedResources.contains(ResourceType.Patient))
                },
            )
        }
    }

    @Test
    fun `publish events honor batch size override with matching resource type`() {
        val kafkaLoadService: KafkaLoadService = mockk()
        val kafkaPublishService: KafkaPublishService = mockk()
        val tenantConfigService: TenantConfigurationService = mockk()
        val diagnosticReportPublish: DiagnosticReportPublish = mockk()
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
                "interop-mirth-diagnostic-report_group",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            DiagnosticReportLoad(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                diagnosticReportPublish,
                kafkaDagPublisher,
            )

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(3, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }
}
