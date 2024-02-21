package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.destinations.EncounterPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EncounterLoadTest {
    private lateinit var channel: EncounterLoad

    @BeforeEach
    fun setup() {
        channel = EncounterLoad(mockk(), mockk(), mockk(), mockk())
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @Test
    fun `create channel - works`() {
        Assertions.assertEquals("EncounterLoad", channel.rootName)
        Assertions.assertEquals("interop-mirth-encounter_group", channel.channelGroupId)
        Assertions.assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `publish events honor batch size override with matching resource type`() {
        val kafkaLoadService: KafkaLoadService = mockk()
        val kafkaPublishService: KafkaPublishService = mockk()
        val tenantConfigService: TenantConfigurationService = mockk()
        val encounterPublish: EncounterPublish = mockk()

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
                "interop-mirth-encounter_group",
            )
        } returns
            listOf(
                patientEventTenant1Run1,
                patientEvent2Tenant1Run1,
                patientEvent3Tenant1Run1,
            )

        every { JacksonUtil.writeJsonValue(any()) } returns "data"

        val channel =
            EncounterLoad(
                kafkaPublishService,
                kafkaLoadService,
                tenantConfigService,
                encounterPublish,
            )

        val messages = channel.channelSourceReader(emptyMap())
        Assertions.assertEquals(3, messages.size)

        messages.forEach {
            Assertions.assertEquals("tenant1", it.dataMap[MirthKey.TENANT_MNEMONIC.code])
            Assertions.assertEquals("run1", it.dataMap[MirthKey.EVENT_RUN_ID.code])
        }
    }
}
