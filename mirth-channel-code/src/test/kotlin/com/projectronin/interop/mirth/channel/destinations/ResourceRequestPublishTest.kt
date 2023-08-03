package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.event.interop.resource.request.v1.InteropResourceRequestV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime

class ResourceRequestPublishTest {
    lateinit var destination: ResourceRequestPublish
    lateinit var tenantService: TenantService
    lateinit var kafkaLoadService: KafkaLoadService
    val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "tenny"
    }

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        tenantService = mockk {
            every { getTenantForMnemonic("fake") } returns null
            every { getTenantForMnemonic("tenny") } returns mockTenant
        }
        destination = ResourceRequestPublish(kafkaLoadService, tenantService)
        mockkObject(JacksonUtil)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `bad tenant is bad`() {
        assertThrows<IllegalArgumentException> {
            destination.channelDestinationWriter("fake", "", emptyMap(), emptyMap())
        }
    }

    @Test
    fun `channel works`() {
        val mockEvent = mockk<InteropResourceRequestV1> {
            every { resourceType } returns "Patient"
            every { resourceFHIRId } returns "anything"
            every { flowOptions } returns null
        }
        every { JacksonUtil.readJsonObject("event", InteropResourceRequestV1::class) } returns mockEvent
        every {
            kafkaLoadService.pushLoadEvent(
                tenantId = "tenny",
                resourceType = ResourceType.Patient,
                resourceFHIRIds = listOf("anything"),
                trigger = DataTrigger.AD_HOC,
                metadata = any()
            )
        } returns mockk {
            every { failures } returns emptyList()
            every { successful } returns listOf("Success")
        }
        val results =
            destination.channelDestinationWriter("tenny", "event", emptyMap(), emptyMap())
        assertEquals(MirthResponseStatus.SENT, results.status)
        assertEquals("Published to Load Topic", results.message)
    }

    @Test
    fun `channel works with flow options on request`() {
        val registryMinimum = OffsetDateTime.now()
        val mockEvent = mockk<InteropResourceRequestV1> {
            every { resourceType } returns "Patient"
            every { resourceFHIRId } returns "anything"
            every { flowOptions } returns InteropResourceRequestV1.FlowOptions(
                disableDownstreamResources = true,
                normalizationRegistryMinimumTime = registryMinimum
            )
        }
        every { JacksonUtil.readJsonObject("event", InteropResourceRequestV1::class) } returns mockEvent

        val loadFlowOptions = InteropResourceLoadV1.FlowOptions(
            disableDownstreamResources = true,
            normalizationRegistryMinimumTime = registryMinimum
        )
        every {
            kafkaLoadService.pushLoadEvent(
                tenantId = "tenny",
                resourceType = ResourceType.Patient,
                resourceFHIRIds = listOf("anything"),
                trigger = DataTrigger.AD_HOC,
                metadata = any(),
                flowOptions = loadFlowOptions
            )
        } returns mockk {
            every { failures } returns emptyList()
            every { successful } returns listOf("Success")
        }
        val results =
            destination.channelDestinationWriter("tenny", "event", emptyMap(), emptyMap())
        assertEquals(MirthResponseStatus.SENT, results.status)
        assertEquals("Published to Load Topic", results.message)
    }

    @Test
    fun `channel handles errors`() {
        val mockEvent = mockk<InteropResourceRequestV1> {
            every { resourceType } returns "Patient"
            every { resourceFHIRId } returns "anything"
            every { flowOptions } returns null
        }
        every { JacksonUtil.readJsonObject("event", InteropResourceRequestV1::class) } returns mockEvent
        every { JacksonUtil.writeJsonValue(any()) } returns "failed"
        every {
            kafkaLoadService.pushLoadEvent(
                tenantId = "tenny",
                resourceType = ResourceType.Patient,
                resourceFHIRIds = listOf("anything"),
                trigger = DataTrigger.AD_HOC,
                metadata = any()
            )
        } returns mockk {
            every { failures } returns listOf(mockk {})
            every { successful } returns emptyList()
        }
        val results =
            destination.channelDestinationWriter("tenny", "event", emptyMap(), emptyMap())
        assertEquals(MirthResponseStatus.ERROR, results.status)
        assertEquals("Failed to publish to Load Topic", results.message)
    }
}
