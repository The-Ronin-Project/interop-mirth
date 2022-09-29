package com.projectronin.interop.mirth.channel

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
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
    private lateinit var mockVendorFactory: VendorFactory
    private lateinit var mockPatientService: PatientService
    private lateinit var mockPractitionerService: PractitionerService
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: AppointmentQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockVendorFactory = mockk()
        mockPatientService = mockk()
        mockPractitionerService = mockk()
        mockServiceFactory = mockk {
            every { vendorFactory(mockTenant) } returns mockVendorFactory
            every { patientService() } returns mockPatientService
            every { practitionerService() } returns mockPractitionerService
        }
        channel = AppointmentQueue(mockServiceFactory)
    }

    @Test
    fun `create channel - works`() {
        val channel = AppointmentQueue(mockServiceFactory)
        assertEquals(ResourceType.APPOINTMENT, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        mockkStatic(Appointment::transformTo)
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns mockk {
            every { transformTo(RoninAppointment, any()) } returns null
        }
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
        mockkStatic(Appointment::transformTo)
        val mockAppointment = mockk<Appointment>()
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns mockk {
            every { transformTo(RoninAppointment, any()) } returns mockAppointment
        }
        val transformedAppt = channel.deserializeAndTransform("appointmentString", mockTenant)
        assertEquals(mockAppointment, transformedAppt)
    }
}
