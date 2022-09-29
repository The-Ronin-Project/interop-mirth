package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val VALID_TENANT_ID = "mdaoc"
private const val CHANNEL_ROOT_NAME = "AppointmentByPractitionerLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class AppointmentByPractitionerPatientWriterTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var serviceFactory: ServiceFactory
    lateinit var writer: AppointmentByPractitionerPatientWriter

    private val tenant = mockk<Tenant> {
        every { mnemonic } returns VALID_TENANT_ID
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
        unmockkObject(RoninPatient)
    }

    @BeforeEach
    fun setup() {
        vendorFactory = mockk()

        serviceFactory = mockk {
            every { getTenant(VALID_TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
        }

        writer = AppointmentByPractitionerPatientWriter(CHANNEL_ROOT_NAME, serviceFactory)
    }

    @Test
    fun `destinationFilter - works when no value found`() {
        assertFalse(writer.destinationFilter(VALID_DEPLOYED_NAME, "", emptyMap(), emptyMap()).result)
    }

    @Test
    fun `destinationFilter - works when value found`() {
        assertTrue(
            writer.destinationFilter(
                VALID_DEPLOYED_NAME,
                "",
                mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient"),
                emptyMap()
            ).result
        )
    }

    @Test
    fun `destinationWriter - works`() {

        mockkStatic(Patient::transformTo)
        mockkObject(RoninPatient)
        mockkObject(JacksonUtil)
        val mockRoninPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }
        every { RoninPatient.create(any()) } returns mockk()
        val mockPatient = mockk<Patient> {
            every { transformTo(any(), tenant) } returns mockRoninPatient
        }

        every { vendorFactory.identifierService } returns mockk()
        val mockPublishService = mockk<PublishService> {
            every { publishFHIRResources(VALID_TENANT_ID, listOf(mockRoninPatient)) } returns true
        }

        every { serviceFactory.publishService() } returns mockPublishService
        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        val response = writer.destinationWriter(
            VALID_DEPLOYED_NAME, "", mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient"), emptyMap()
        )
        assertEquals("Published 1 Patient(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals("[]", response.detailedMessage)
        assertEquals("blah", response.dataMap[MirthKey.PATIENT_FHIR_ID.code])
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        mockkStatic(Patient::transformTo)
        mockkObject(RoninPatient)
        mockkObject(JacksonUtil)
        val mockRoninPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }
        every { RoninPatient.create(any()) } returns mockk()
        val mockPatient = mockk<Patient> {
            every { transformTo(any(), tenant) } returns mockRoninPatient
        }

        every { vendorFactory.identifierService } returns mockk()
        val mockPublishService = mockk<PublishService> {
            every { publishFHIRResources(VALID_TENANT_ID, listOf(mockRoninPatient)) } returns false
        }

        every { serviceFactory.publishService() } returns mockPublishService
        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        val response = writer.destinationWriter(
            VALID_DEPLOYED_NAME, "", mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient"), emptyMap()
        )
        assertEquals("Failed to publish Patient(s)", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("[]", response.detailedMessage)
        assertNull(response.dataMap[MirthKey.PATIENT_FHIR_ID.code])
    }

    @Test
    fun `destinationWriter - failed transform`() {
        mockkStatic(Patient::transformTo)
        mockkObject(RoninPatient)
        mockkObject(JacksonUtil)
        val mockRoninPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }
        every { RoninPatient.create(any()) } returns mockk()
        val mockPatient = mockk<Patient> {
            every { transformTo(any(), tenant) } returns null
        }

        every { vendorFactory.identifierService } returns mockk()
        val mockPublishService = mockk<PublishService> {
            every { publishFHIRResources(VALID_TENANT_ID, listOf(mockRoninPatient)) } returns true
        }

        every { serviceFactory.publishService() } returns mockPublishService
        every { JacksonUtil.writeJsonValue(any()) } returns "Oh No"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        val response = writer.destinationWriter(
            VALID_DEPLOYED_NAME, "", mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient"), emptyMap()
        )
        assertEquals("Failed to transform Patient", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("Oh No", response.detailedMessage)
        assertNull(response.dataMap[MirthKey.PATIENT_FHIR_ID.code])
    }
}
