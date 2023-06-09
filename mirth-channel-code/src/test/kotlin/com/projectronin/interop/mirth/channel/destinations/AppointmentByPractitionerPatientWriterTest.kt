package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.channel.util.serialize
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val VALID_TENANT_ID = "mdaoc"

class AppointmentByPractitionerPatientWriterTest {
    lateinit var transformManager: TransformManager
    lateinit var publishService: PublishService
    lateinit var roninPatient: RoninPatient
    lateinit var writer: AppointmentByPractitionerPatientWriter

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
        roninPatient = mockk()

        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic(VALID_TENANT_ID) } returns tenant
        }
        writer = AppointmentByPractitionerPatientWriter(tenantService, transformManager, publishService, roninPatient)
    }

    @Test
    fun `destinationFilter - works when no value found`() {
        assertFalse(
            writer.destinationFilter(
                "unused",
                "",
                mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID),
                emptyMap()
            ).result
        )
    }

    @Test
    fun `destinationFilter - works when value found`() {
        assertTrue(
            writer.destinationFilter(
                "unused",
                "",
                mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient", MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID),
                emptyMap()
            ).result
        )
    }

    @Test
    fun `destinationWriter - works`() {
        mockkObject(JacksonUtil)
        val mockRoninPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }

        val mockPatient = mockk<Patient>()

        every { transformManager.transformResource(mockPatient, roninPatient, tenant) } returns mockRoninPatient

        val metadata = generateMetadata()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, listOf(mockRoninPatient), metadata) } returns true

        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(
                MirthKey.NEW_PATIENT_JSON.code to "patient",
                MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
                MirthKey.EVENT_METADATA.code to serialize(metadata)
            ),
            emptyMap()
        )
        assertEquals("Published 1 Patient(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals("[]", response.detailedMessage)
        assertEquals("blah", response.dataMap[MirthKey.PATIENT_FHIR_ID.code])
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        mockkObject(JacksonUtil)
        val mockRoninPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }

        val mockPatient = mockk<Patient>()
        every { transformManager.transformResource(mockPatient, roninPatient, tenant) } returns mockRoninPatient

        val metadata = generateMetadata()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, listOf(mockRoninPatient), metadata) } returns false

        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(
                MirthKey.NEW_PATIENT_JSON.code to "patient",
                MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
                MirthKey.EVENT_METADATA.code to serialize(metadata)
            ),
            emptyMap()
        )
        assertEquals("Failed to publish Patient(s)", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("[]", response.detailedMessage)
        assertNull(response.dataMap[MirthKey.PATIENT_FHIR_ID.code])
    }

    @Test
    fun `destinationWriter - failed transform`() {
        mockkObject(JacksonUtil)
        val mockPatient = mockk<Patient>()

        every { transformManager.transformResource(mockPatient, roninPatient, tenant) } returns null

        every { JacksonUtil.writeJsonValue(any()) } returns "Oh No"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient", MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID),
            emptyMap()
        )
        assertEquals("Failed to transform Patient", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("Oh No", response.detailedMessage)
        assertNull(response.dataMap[MirthKey.PATIENT_FHIR_ID.code])
    }

    @Test
    fun `destinationWriter - errors when null patient id value`() {
        mockkObject(JacksonUtil)
        val mockRoninPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns null
            }
        }

        val mockPatient = mockk<Patient>()
        every { transformManager.transformResource(mockPatient, roninPatient, tenant) } returns mockRoninPatient

        val metadata = generateMetadata()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, listOf(mockRoninPatient), metadata) } returns false

        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        assertThrows<java.lang.NullPointerException> {
            writer.destinationWriter(
                "unused",
                "",
                mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient", MirthKey.EVENT_METADATA.code to serialize(metadata)),
                emptyMap()
            )
        }
    }

    @Test
    fun `destinationWriter - errors when null patient id`() {
        mockkObject(JacksonUtil)
        val mockRoninPatient = mockk<Patient> {
            every { id } returns null
        }

        val mockPatient = mockk<Patient>()
        every { transformManager.transformResource(mockPatient, roninPatient, tenant) } returns mockRoninPatient

        val metadata = generateMetadata()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, listOf(mockRoninPatient), metadata) } returns false

        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockPatient
        assertThrows<java.lang.NullPointerException> {
            writer.destinationWriter(
                "unused",
                "",
                mapOf(MirthKey.NEW_PATIENT_JSON.code to "patient", MirthKey.EVENT_METADATA.code to serialize(metadata)),
                emptyMap()
            )
        }
    }
}
