package com.projectronin.interop.mirth.channel

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Participant
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.ParticipationStatus
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.util.asCode
import com.projectronin.interop.mirth.connector.ServiceFactory
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
    private lateinit var mockVendorFactory: VendorFactory
    private lateinit var mockPatientService: PatientService
    private lateinit var mockPractitionerService: PractitionerService
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockConceptMapClient: ConceptMapClient
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: AppointmentQueue
    private lateinit var mockR4Appointment: Appointment

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockVendorFactory = mockk()
        mockPatientService = mockk()
        mockPractitionerService = mockk()
        mockTransformManager = mockk()
        mockConceptMapClient = mockk()
        mockServiceFactory = mockk {
            every { vendorFactory(mockTenant) } returns mockVendorFactory
            every { patientService() } returns mockPatientService
            every { practitionerService() } returns mockPractitionerService
            every { transformManager() } returns mockTransformManager
            every { conceptMapClient() } returns mockConceptMapClient
        }
        channel = AppointmentQueue(mockServiceFactory)
        mockR4Appointment = mockk(relaxed = true) {
            every { resourceType } returns "Appointment"
            every { id } returns Id("12345")
            every { identifier } returns listOf(Identifier(value = "id".asFHIR()))
            every { status } returns AppointmentStatus.CANCELLED.asCode()
            every { participant } returns listOf(
                Participant(
                    actor = Reference(display = "actor".asFHIR()),
                    status = ParticipationStatus.ACCEPTED.asCode()
                )
            )
        }
    }

    @Test
    fun `create channel - works`() {
        val channel = AppointmentQueue(mockServiceFactory)
        assertEquals(ResourceType.APPOINTMENT, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(RoninAppointment)
        mockkObject(JacksonUtil)

        val appointment = mockk<Appointment>()
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns appointment
        val roninAppointment = mockk<RoninAppointment>()
        every { RoninAppointment.create(any()) } returns roninAppointment

        every { mockTransformManager.transformResource(appointment, roninAppointment, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "appointmentString",
                mockTenant
            )
        }
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(RoninAppointment)
        mockkObject(JacksonUtil)

        val appointment = mockk<Appointment>()
        every { JacksonUtil.readJsonObject<Appointment>(any(), any()) } returns appointment
        val roninAppointment = mockk<RoninAppointment>()
        every { RoninAppointment.create(any()) } returns roninAppointment

        val transformedAppointment = mockk<Appointment>()
        every {
            mockTransformManager.transformResource(
                appointment,
                roninAppointment,
                mockTenant
            )
        } returns transformedAppointment

        val transformedAppt = channel.deserializeAndTransform("appointmentString", mockTenant)
        assertEquals(transformedAppointment, transformedAppt)
    }
}
