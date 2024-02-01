package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.destinations.DocumentReferencePublish
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

class DocumentReferenceLoadTest {
    private lateinit var channel: DocumentReferenceLoad

    @BeforeEach
    fun setup() {
        channel = DocumentReferenceLoad(mockk(), mockk(), mockk(), mockk())
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @Test
    fun `create channel - works`() {
        assertEquals("DocumentReferenceLoad", channel.rootName)
        assertEquals("interop-mirth-document_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `publish events honor batch size override with matching resource type`() {
        val kafkaLoadService: KafkaLoadService = mockk()
        val kafkaPublishService: KafkaPublishService = mockk()
        val tenantConfigService: TenantConfigurationService = mockk()
        val documentReferencePublish: DocumentReferencePublish = mockk()

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
                "interop-mirth-document_group",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )
        every {
            kafkaPublishService.retrievePublishEvents(
                ResourceType.DocumentReference,
                DataTrigger.NIGHTLY,
                "interop-mirth-document_group",
            )
        } returns emptyList()

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            DocumentReferenceLoad(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                documentReferencePublish,
            )

        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(3, messages.size)

        messages.forEach {
            assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }
}
