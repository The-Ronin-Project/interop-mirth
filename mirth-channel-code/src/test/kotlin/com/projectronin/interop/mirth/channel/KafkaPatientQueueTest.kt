package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.destinations.queue.PatientTenantlessQueueWriter
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.rcdm.transform.TransformManager
import com.projectronin.interop.rcdm.transform.model.TransformResponse
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

class KafkaPatientQueueTest {
    private val mockTenant =
        mockk<Tenant> {
            every { mnemonic } returns "testmnemonic"
        }
    private lateinit var mockTransformManager: TransformManager
    private lateinit var channel: KafkaPatientQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockTransformManager = mockk()
        val tenantService =
            mockk<TenantService> {
                every { getTenantForMnemonic("testmnemonic") } returns mockTenant
            }
        val queueService = mockk<KafkaQueueService>()
        val queueWriter = mockk<PatientTenantlessQueueWriter>()

        channel = KafkaPatientQueue(tenantService, queueService, queueWriter, mockTransformManager)
    }

    @Test
    fun `create channel - works`() {
        assertEquals(ResourceType.PATIENT, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)

        val mockPatient = mockk<Patient>()
        every { JacksonUtil.readJsonObject<Patient>(any(), any()) } returns mockPatient

        val roninPatient = mockk<Patient>()
        val transformResponse = TransformResponse(roninPatient)
        every {
            mockTransformManager.transformResource(
                mockPatient,
                mockTenant,
            )
        } returns transformResponse

        val transformedPatient = channel.deserializeAndTransform("patientString", mockTenant)
        assertEquals(transformResponse, transformedPatient)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)

        val mockPatient = mockk<Patient>()
        every { JacksonUtil.readJsonObject<Patient>(any(), any()) } returns mockPatient

        every { mockTransformManager.transformResource(mockPatient, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "patientString",
                mockTenant,
            )
        }
    }
}
