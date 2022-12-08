package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
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
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.publishers.PublishService
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

private const val VALID_TENANT_ID = "mdaoc"
private const val CHANNEL_ROOT_NAME = "AppointmentByPractitionerLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class AppointmentByPractitionerAppointmentWriterTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var conceptMapClient: ConceptMapClient
    lateinit var serviceFactory: ServiceFactory
    lateinit var writer: AppointmentByPractitionerAppointmentWriter
    lateinit var roninAppointment: RoninAppointment
    lateinit var mockR4Appointment: Appointment

    private val tenant = mockk<Tenant> {
        every { mnemonic } returns VALID_TENANT_ID
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        vendorFactory = mockk()
        conceptMapClient = mockk()
        serviceFactory = mockk {
            every { getTenant(VALID_TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
            every { conceptMapClient() } returns conceptMapClient
        }
        writer = AppointmentByPractitionerAppointmentWriter(CHANNEL_ROOT_NAME, serviceFactory)
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
    fun `destinationTransformer - works`() {
        mockkObject(RoninAppointment)
        mockkStatic(RoninAppointment::transform)
        mockkStatic(ConceptMapClient::getConceptMapping)
        val mockAppointment = mockk<Appointment>()
        val mockRonin = mockk<RoninAppointment> {
            every { transform(mockR4Appointment, tenant) } returns mockAppointment
        }
        every { RoninAppointment.create(any()) } returns mockRonin
        writer = AppointmentByPractitionerAppointmentWriter(CHANNEL_ROOT_NAME, serviceFactory)

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("appointmentString", Appointment::class) } returns listOf(mockR4Appointment)
        every { JacksonUtil.writeJsonValue(listOf(mockAppointment)) } returns "listOfSerializedAppointments"

        val result = writer.destinationTransformer(
            VALID_DEPLOYED_NAME, "appointmentString", emptyMap(), emptyMap()
        )
        assertEquals("listOfSerializedAppointments", result.message)
        assertEquals(mapOf(MirthKey.FAILURE_COUNT.code to 0), result.dataMap)

        unmockkObject(RoninAppointment)
        unmockkStatic(RoninAppointment::transform)
        unmockkStatic(ConceptMapClient::getConceptMapping)
    }

    @Test
    fun `destinationTransformer - transformation fails`() {
        mockkObject(RoninAppointment)
        mockkStatic(RoninAppointment::transform)
        mockkStatic(ConceptMapClient::getConceptMapping)
        val mockAppointment = mockk<Appointment>()
        val mockRonin = mockk<RoninAppointment> {
            every { transform(mockR4Appointment, tenant) } returns null
        }
        every { RoninAppointment.create(any()) } returns mockRonin
        writer = AppointmentByPractitionerAppointmentWriter(CHANNEL_ROOT_NAME, serviceFactory)

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("appointmentString", Appointment::class) } returns listOf(mockR4Appointment)
        every { JacksonUtil.writeJsonValue(listOf(mockAppointment)) } returns "listOfSerializedAppointments"

        assertThrows<ResourcesNotTransformedException> {
            writer.destinationTransformer(
                VALID_DEPLOYED_NAME, "appointmentString", emptyMap(), emptyMap()
            )
        }

        unmockkObject(RoninAppointment)
        unmockkStatic(RoninAppointment::transform)
        unmockkStatic(ConceptMapClient::getConceptMapping)
    }

    @Test
    fun `destinationWriter - works`() {
        roninAppointment = mockk {
            every { transform(mockR4Appointment, tenant) } returns null
        }

        val mockPublishService = mockk<PublishService> {
            every { publishFHIRResources(VALID_TENANT_ID, any()) } returns true
        }
        every { serviceFactory.publishService() } returns mockPublishService

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("appointmentString", Appointment::class) } returns listOf(mockR4Appointment)
        every { JacksonUtil.writeJsonValue(any()) } returns "listOfSerializedAppointments"

        val response = writer.destinationWriter(
            VALID_DEPLOYED_NAME, "appointmentString", emptyMap(), emptyMap()
        )
        assertEquals("Published 1 Appointment(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals("listOfSerializedAppointments", response.detailedMessage)
    }

    @Test
    fun `destinationWriter - nothing transformed to publish`() {
        roninAppointment = mockk {
            every { transform(mockR4Appointment, tenant) } returns null
        }

        val mockPublishService = mockk<PublishService> {
            every { publishFHIRResources(VALID_TENANT_ID, any()) } returns false
        }
        every { serviceFactory.publishService() } returns mockPublishService

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("appointmentString", Appointment::class) } returns listOf()
        every { JacksonUtil.writeJsonValue(any()) } returns "listOfSerializedAppointments"

        val response = writer.destinationWriter(
            VALID_DEPLOYED_NAME, "appointmentString", emptyMap(), emptyMap()
        )
        assertEquals("No transformed Appointment(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("appointmentString", response.detailedMessage)
    }
}
