package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.destinations.ServiceRequestPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServiceRequestLoadTest {
    private lateinit var channel: ServiceRequestLoad

    @BeforeEach
    fun setup() {
        channel = ServiceRequestLoad(mockk(), mockk(), mockk(), mockk())
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @Test
    fun `channel creation works`() {
        assertEquals("ServiceRequestLoad", channel.rootName)
        assertEquals("interop-mirth-service-request_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `publish events honor batch size override with matching resource type`() {
        val kafkaLoadService: KafkaLoadService = mockk()
        val kafkaPublishService: KafkaPublishService = mockk()
        val tenantConfigService: TenantConfigurationService = mockk()
        val serviceRequestPublish: ServiceRequestPublish = mockk()

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
                every { targetedResources } returns listOf("Patient")
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
                "interop-mirth-service-request_group",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.MedicationRequest,
                DataTrigger.NIGHTLY,
                "interop-mirth-service-request_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Encounter,
                DataTrigger.NIGHTLY,
                "interop-mirth-service-request_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Appointment,
                DataTrigger.NIGHTLY,
                "interop-mirth-service-request_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.DiagnosticReport,
                DataTrigger.NIGHTLY,
                "interop-mirth-service-request_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.MedicationStatement,
                DataTrigger.NIGHTLY,
                "interop-mirth-service-request_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Observation,
                DataTrigger.NIGHTLY,
                "interop-mirth-service-request_group",
            )
        } returns emptyList()
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.Procedure,
                DataTrigger.NIGHTLY,
                "interop-mirth-service-request_group",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            ServiceRequestLoad(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                serviceRequestPublish,
            )

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(3, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }
}
