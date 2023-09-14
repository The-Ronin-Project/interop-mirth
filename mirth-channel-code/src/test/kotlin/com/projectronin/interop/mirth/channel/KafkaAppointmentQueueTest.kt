package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.destinations.queue.AppointmentTenantlessQueueWriter
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

class KafkaAppointmentQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockRoninAppointment: RoninAppointment
    private lateinit var channel: KafkaAppointmentQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockTransformManager = mockk()
        mockRoninAppointment = mockk()
        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic("testmnemonic") } returns mockTenant
        }
        val queueService = mockk<KafkaQueueService>()
        val queueWriter = mockk<AppointmentTenantlessQueueWriter>()

        channel = KafkaAppointmentQueue(tenantService, queueService, queueWriter, mockTransformManager, mockRoninAppointment)
    }

    @Test
    fun `create channel - works`() {
        assertEquals(ResourceType.APPOINTMENT, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)

        val mockAppointment = mockk<Appointment>()
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns mockAppointment

        val roninAppointment = mockk<Appointment>()
        every {
            mockTransformManager.transformResource(
                mockAppointment,
                mockRoninAppointment,
                mockTenant
            )
        } returns roninAppointment

        val transformedAppointment = channel.deserializeAndTransform("conditionString", mockTenant)
        assertEquals(roninAppointment, transformedAppointment)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)

        val mockAppointment = mockk<Appointment>()
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns mockAppointment

        every { mockTransformManager.transformResource(mockAppointment, mockRoninAppointment, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "conditionString",
                mockTenant
            )
        }
    }
}
