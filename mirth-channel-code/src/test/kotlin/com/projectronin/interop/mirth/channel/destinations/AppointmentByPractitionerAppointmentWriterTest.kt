package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.publishers.PublishService
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

private const val VALID_TENANT_ID = "mdaoc"
private const val CHANNEL_ROOT_NAME = "AppointmentByPractitionerLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class AppointmentByPractitionerAppointmentWriterTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var transformManager: TransformManager
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
        transformManager = mockk()
        conceptMapClient = mockk()

        serviceFactory = mockk {
            every { getTenant(VALID_TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
            every { transformManager() } returns transformManager
            every { conceptMapClient() } returns conceptMapClient
        }
        writer = AppointmentByPractitionerAppointmentWriter(CHANNEL_ROOT_NAME, serviceFactory)
    }

    @Test
    fun `destinationTransformer - works`() {
        val mockRoninAppointment = mockk<Appointment>()
        val mockR4Appointment = mockk<Appointment>()

        mockkObject(RoninAppointment)
        val roninAppointment = mockk<RoninAppointment>()
        every { RoninAppointment.create(any()) } returns roninAppointment

        every {
            transformManager.transformResource(
                mockR4Appointment,
                roninAppointment,
                tenant
            )
        } returns mockRoninAppointment

        val message = "msg"
        mockkObject(JacksonUtil)

        every { JacksonUtil.readJsonList(message, Appointment::class) } returns listOf(mockR4Appointment)
        every { JacksonUtil.writeJsonValue(listOf(mockRoninAppointment)) } returns "[]"

        val result = writer.destinationTransformer(
            VALID_DEPLOYED_NAME, message, emptyMap(), emptyMap()
        )
        assertEquals("[]", result.message)
    }

    @Test
    fun `destinationTransformer - trasnformation fails`() {
        val mockR4Appointment = mockk<Appointment> {
            every { id } returns Id("12345")
        }

        mockkObject(RoninAppointment)
        val roninAppointment = mockk<RoninAppointment>()
        every { RoninAppointment.create(any()) } returns roninAppointment

        every {
            transformManager.transformResource(
                mockR4Appointment,
                roninAppointment,
                tenant
            )
        } returns null

        val message = "msg"
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList(message, Appointment::class) } returns listOf(mockR4Appointment)

        assertThrows<ResourcesNotTransformedException> {
            writer.destinationTransformer(
                VALID_DEPLOYED_NAME, message, emptyMap(), emptyMap()
            )
        }
    }

    @Test
    fun `destinationWriter - works`() {
        val mockR4Appointment = mockk<Appointment> {
            every { id } returns Id("12345")
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
