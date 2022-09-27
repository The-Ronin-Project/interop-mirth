package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.IdentifierService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
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

class PatientQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockVendorFactory: VendorFactory
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: PatientQueue
    private lateinit var mockIdentifierService: IdentifierService

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
        unmockkObject(RoninPatient)
    }

    @BeforeEach
    fun setup() {
        mockIdentifierService = mockk {
            every { getPatientIdentifier(mockTenant, any()) } returns mockk {
                every { value } returns "An MRN"
            }
        }
        mockVendorFactory = mockk {
            every { identifierService } returns mockIdentifierService
        }
        mockServiceFactory = mockk {
            every { vendorFactory(mockTenant) } returns mockVendorFactory
        }
        channel = PatientQueue(mockServiceFactory)
    }

    @Test
    fun `create channel - works`() {
        val channel = PatientQueue(mockServiceFactory)
        assertEquals(ResourceType.PATIENT, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)
        mockkObject(RoninPatient)
        mockkStatic(Patient::transformTo)
        val mockOnc = mockk<RoninPatient>()
        val mockPatient = mockk<Patient>()
        every { JacksonUtil.readJsonObject<Patient>(any(), any()) } returns mockk()
        every { RoninPatient.create(any()) } returns mockOnc
        every { any<Patient>().transformTo(any(), mockTenant) } returns mockPatient
        val transformedPatient = channel.deserializeAndTransform("patientString", mockTenant)
        assertEquals(mockPatient, transformedPatient)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        mockkObject(RoninPatient)
        mockkStatic(Patient::transformTo)
        val mockOnc = mockk<RoninPatient>()
        every { JacksonUtil.readJsonObject<Patient>(any(), any()) } returns mockk()
        every { RoninPatient.create(any()) } returns mockOnc
        every { any<Patient>().transformTo(any(), mockTenant) } returns null
        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "patientString",
                mockTenant
            )
        }
    }
}
