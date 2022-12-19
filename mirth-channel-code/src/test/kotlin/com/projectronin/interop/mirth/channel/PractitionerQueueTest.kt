package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.mirth.channel.destinations.queue.PractitionerQueueWriter
import com.projectronin.interop.queue.QueueService
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

class PractitionerQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockRoninPractitioner: RoninPractitioner
    private lateinit var channel: PractitionerQueue

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
        val practitionerQueueWriter = mockk<PractitionerQueueWriter>()
        val queueService = mockk<QueueService>()
        channel = PractitionerQueue(
            tenantService,
            mockTransformManager,
            practitionerQueueWriter,
            queueService,
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
        val practitioner = mockk<Practitioner>()
        every { JacksonUtil.readJsonObject<Practitioner>(any(), any()) } returns practitioner

        val transformedPractitioner = mockk<Practitioner>()
        every {
            mockTransformManager.transformResource(
                practitioner,
                mockRoninPractitioner,
                mockTenant
            )
        } returns transformedPractitioner

        val transformed = channel.deserializeAndTransform("practitionerString", mockTenant)
        assertEquals(transformedPractitioner, transformed)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        val practitioner = mockk<Practitioner>()
        every { JacksonUtil.readJsonObject<Practitioner>(any(), any()) } returns practitioner

        every { mockTransformManager.transformResource(practitioner, mockRoninPractitioner, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "practitionerString",
                mockTenant
            )
        }
    }
}
