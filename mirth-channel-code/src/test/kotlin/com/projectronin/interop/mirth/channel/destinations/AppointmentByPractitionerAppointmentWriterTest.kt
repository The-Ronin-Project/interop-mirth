package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.publishers.PublishService
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

private const val VALID_TENANT_ID = "mdaoc"

class AppointmentByPractitionerAppointmentWriterTest {
    lateinit var transformManager: TransformManager
    lateinit var publishService: PublishService
    lateinit var writer: AppointmentByPractitionerAppointmentWriter
    lateinit var roninAppointment: RoninAppointment

    private val tenant = mockk<Tenant> {
        every { mnemonic } returns VALID_TENANT_ID
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        transformManager = mockk()
        publishService = mockk()
        roninAppointment = mockk()

        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic(VALID_TENANT_ID) } returns tenant
        }
        writer = AppointmentByPractitionerAppointmentWriter(
            tenantService,
            transformManager,
            publishService,
            roninAppointment
        )
    }

    @Test
    fun `destinationTransformer - works`() {
        val mockRoninAppointment = mockk<Appointment>()
        val mockR4Appointment = mockk<Appointment>()

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

        val result =
            writer.destinationTransformer(
                "unused",
                message,
                mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID),
                emptyMap()
            )
        assertEquals("[]", result.message)
    }

    @Test
    fun `destinationTransformer - trasnformation fails`() {
        val mockR4Appointment = mockk<Appointment> {
            every { id } returns Id("12345")
        }

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
                "unused",
                message,
                mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID),
                emptyMap()
            )
        }
    }

    @Test
    fun `destinationWriter - works`() {
        val mockR4Appointment = mockk<Appointment> {
            every { id } returns Id("12345")
        }

        val metadata = mockk<Metadata>()

        every { publishService.publishFHIRResources(VALID_TENANT_ID, any(), metadata) } returns true

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("appointmentString", Appointment::class) } returns listOf(mockR4Appointment)
        every { JacksonUtil.writeJsonValue(any()) } returns "listOfSerializedAppointments"

        val response = writer.destinationWriter(
            "unused",
            "appointmentString",
            mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID, "kafkaEventMetadata" to metadata),
            emptyMap()
        )
        assertEquals("Published 1 Appointment(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals("listOfSerializedAppointments", response.detailedMessage)
    }

    @Test
    fun `destinationWriter - nothing transformed to publish`() {
        val metadata = mockk<Metadata>()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, any(), metadata) } returns false

        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("appointmentString", Appointment::class) } returns listOf()
        every { JacksonUtil.writeJsonValue(any()) } returns "listOfSerializedAppointments"

        val response = writer.destinationWriter(
            "unused",
            "appointmentString",
            mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID, "kafkaEventMetadata" to metadata),
            emptyMap()
        )
        assertEquals("No transformed Appointment(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("appointmentString", response.detailedMessage)
    }
}
