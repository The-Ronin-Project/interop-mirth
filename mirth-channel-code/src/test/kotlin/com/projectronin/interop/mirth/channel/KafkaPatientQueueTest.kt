package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.IdentifierService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
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
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class KafkaPatientQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockVendorFactory: VendorFactory
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: KafkaPatientQueue
    private lateinit var mockIdentifierService: IdentifierService
    private lateinit var conceptMapClient: ConceptMapClient

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
        unmockkObject(RoninPatient)
    }

    @BeforeEach
    fun setup() {
        conceptMapClient = mockk()

        mockIdentifierService = mockk {
            every { getPatientIdentifier(mockTenant, any()) } returns mockk {
                every { value } returns "An MRN".asFHIR()
            }
        }
        mockVendorFactory = mockk {
            every { identifierService } returns mockIdentifierService
        }
        mockTransformManager = mockk()
        mockServiceFactory = mockk {
            every { vendorFactory(mockTenant) } returns mockVendorFactory
            every { conceptMapClient() } returns conceptMapClient
            every { transformManager() } returns mockTransformManager
        }
        channel = KafkaPatientQueue(mockServiceFactory)
    }

    @Test
    fun `create channel - works`() {
        val channel = KafkaPatientQueue(mockServiceFactory)
        assertEquals(ResourceType.PATIENT, channel.resourceType)
        assertEquals(1, channel.destinations.size)
        assertDoesNotThrow { KafkaPatientQueue.create() }
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)
        mockkObject(RoninPatient)

        val mockPatient = mockk<Patient>()
        every { JacksonUtil.readJsonObject<Patient>(any(), any()) } returns mockPatient

        val roninPatientProfile = mockk<RoninPatient>()
        every { RoninPatient.create(any(), any()) } returns roninPatientProfile

        val roninPatient = mockk<Patient>()
        every {
            mockTransformManager.transformResource(
                mockPatient,
                roninPatientProfile,
                mockTenant
            )
        } returns roninPatient

        val transformedPatient = channel.deserializeAndTransform("patientString", mockTenant)
        assertEquals(roninPatient, transformedPatient)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        mockkObject(RoninPatient)

        val mockPatient = mockk<Patient>()
        every { JacksonUtil.readJsonObject<Patient>(any(), any()) } returns mockPatient

        val roninPatientProfile = mockk<RoninPatient>()
        every { RoninPatient.create(any(), any()) } returns roninPatientProfile

        every { mockTransformManager.transformResource(mockPatient, roninPatientProfile, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "patientString",
                mockTenant
            )
        }
    }
}
