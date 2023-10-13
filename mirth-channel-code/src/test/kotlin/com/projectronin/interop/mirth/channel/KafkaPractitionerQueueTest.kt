package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.fhir.ronin.transform.TransformResponse
import com.projectronin.interop.mirth.channel.destinations.queue.PractitionerTenantlessQueueWriter
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KafkaPractitionerQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockRoninPractitioner: RoninPractitioner
    private lateinit var channel: KafkaPractitionerQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockTransformManager = mockk()
        mockRoninPractitioner = mockk()
        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic("testmnemonic") } returns mockTenant
        }
        val queueService = mockk<KafkaQueueService>()
        val queueWriter = mockk<PractitionerTenantlessQueueWriter>()

        channel = KafkaPractitionerQueue(
            tenantService,
            queueService,
            queueWriter,
            mockTransformManager,
            mockRoninPractitioner
        )
    }

    @Test
    fun `create channel - works`() {
        assertEquals(ResourceType.PRACTITIONER, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)

        val mockPractitioner = mockk<Practitioner>()
        every { JacksonUtil.readJsonObject<Practitioner>(any(), any()) } returns mockPractitioner

        val roninPractitioner = mockk<Practitioner>()
        val transformResponse = TransformResponse(roninPractitioner)
        every {
            mockTransformManager.transformResource(
                mockPractitioner,
                mockRoninPractitioner,
                mockTenant
            )
        } returns transformResponse

        val transformedPractitioner = channel.deserializeAndTransform("practitionerString", mockTenant)
        assertEquals(transformResponse, transformedPractitioner)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)

        val mockPractitioner = mockk<Practitioner>()
        every { JacksonUtil.readJsonObject<Practitioner>(any(), any()) } returns mockPractitioner

        every {
            mockTransformManager.transformResource(
                mockPractitioner,
                mockRoninPractitioner,
                mockTenant
            )
        } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "practitionerString",
                mockTenant
            )
        }
    }
}
