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
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.util.asCode
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
        mockConceptMapClient = mockk()
        mockServiceFactory = mockk {
            every { vendorFactory(mockTenant) } returns mockVendorFactory
            every { patientService() } returns mockPatientService
            every { practitionerService() } returns mockPractitionerService
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
        mockkStatic(RoninAppointment::transform)
        mockkStatic(ConceptMapClient::getConceptMapping)
        val mockRonin = mockk<RoninAppointment> {
            every { transform(mockR4Appointment, mockTenant) } returns null
        }
        every { RoninAppointment.create(any()) } returns mockRonin

        mockServiceFactory = mockk {
            every { getTenant(mockTenantMnemonic) } returns mockTenant
            every { vendorFactory(mockTenant) } returns mockVendorFactory
            every { conceptMapClient() } returns mockConceptMapClient
        }
        channel = AppointmentQueue(mockServiceFactory)

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonObject("appointmentString", Appointment::class) } returns mockR4Appointment
        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "appointmentString",
                mockTenant
            )
        }

        unmockkObject(RoninAppointment)
        unmockkStatic(RoninAppointment::transform)
        unmockkStatic(ConceptMapClient::getConceptMapping)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(RoninAppointment)
        mockkStatic(RoninAppointment::transform)
        mockkStatic(ConceptMapClient::getConceptMapping)
        val mockAppointment = mockk<Appointment>()
        val mockRonin = mockk<RoninAppointment> {
            every { transform(mockR4Appointment, mockTenant) } returns mockAppointment
        }
        every { RoninAppointment.create(any()) } returns mockRonin

        mockServiceFactory = mockk {
            every { getTenant(mockTenantMnemonic) } returns mockTenant
            every { vendorFactory(mockTenant) } returns mockVendorFactory
            every { conceptMapClient() } returns mockConceptMapClient
        }
        channel = AppointmentQueue(mockServiceFactory)

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonObject("appointmentString", Appointment::class) } returns mockR4Appointment
        val transformedAppointment = channel.deserializeAndTransform("appointmentString", mockTenant)
        assertEquals(mockAppointment, transformedAppointment)

        unmockkObject(RoninAppointment)
        unmockkStatic(RoninAppointment::transform)
        unmockkStatic(ConceptMapClient::getConceptMapping)
    }
}
