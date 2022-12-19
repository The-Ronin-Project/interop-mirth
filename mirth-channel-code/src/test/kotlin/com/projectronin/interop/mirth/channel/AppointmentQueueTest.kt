package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.mirth.channel.destinations.queue.AppointmentQueueWriter
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

class AppointmentQueueTest {
    private val mockTenantMnemonic = "mocky"
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns mockTenantMnemonic
    }

    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockRoninAppointment: RoninAppointment

    private lateinit var channel: AppointmentQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockTransformManager = mockk()
        mockRoninAppointment = mockk()

        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic(mockTenantMnemonic) } returns mockTenant
        }
        val appointmentQueueWriter = mockk<AppointmentQueueWriter>()
        val queueService = mockk<QueueService>()
        channel = AppointmentQueue(
            tenantService,
            mockTransformManager,
            appointmentQueueWriter,
            queueService,
            mockRoninAppointment
        )
    }

    @Test
    fun `create channel - works`() {
        assertEquals(ResourceType.APPOINTMENT, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        val appointment = mockk<Appointment>()
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns appointment

        every { mockTransformManager.transformResource(appointment, mockRoninAppointment, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "appointmentString",
                mockTenant
            )
        }
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)
        val appointment = mockk<Appointment>()
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns appointment

        val transformedAppointment = mockk<Appointment>()
        every {
            mockTransformManager.transformResource(
                appointment,
                mockRoninAppointment,
                mockTenant
            )
        } returns transformedAppointment

        val transformedAppt = channel.deserializeAndTransform("appointmentString", mockTenant)
        assertEquals(transformedAppointment, transformedAppt)
    }
}
