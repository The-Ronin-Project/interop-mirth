package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.base.kafka.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MedicationRequestPublishTest {
    lateinit var tenant: Tenant
    lateinit var destination: MedicationRequestPublish

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        destination = MedicationRequestPublish(mockk(), mockk(), mockk(), mockk(), mockk())
        mockkObject(JacksonUtil)
    }

    @AfterEach
    fun unmockk() {
        unmockkAll()
    }

    @Test
    fun `channel creation works`() {
        assertNotNull(destination)
    }

    @Test
    fun `fails on unknown request`() {
        assertThrows<IllegalStateException> {
            destination.convertEventToRequest("fake", "", mockk(), mockk())
        }
    }

    @Test
    fun `works for load events`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourceLoadV1(
            "tenant",
            "id",
            ResourceType.MedicationRequest,
            InteropResourceLoadV1.DataTrigger.adhoc,
            metadata
        )
        val mockMedicationRequest = mockk<MedicationRequest>()
        every { JacksonUtil.readJsonObject("fake", InteropResourceLoadV1::class) } returns event
        val mockVendorFactory = mockk<VendorFactory> {
            every { medicationRequestService.getByID(tenant, "id") } returns mockMedicationRequest
        }
        val request = destination.convertEventToRequest(
            "fake",
            InteropResourceLoadV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(
            ResourceRequestKey(
                "run123",
                ResourceType.MedicationRequest,
                tenant,
                "id"
            )
        )
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockMedicationRequest, results.first())
    }

    @Test
    fun `works for publish events`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.Patient,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockPatient = mockk<Patient> {
            every { id?.value } returns "tenant-123"
            every { identifier } returns listOf(
                mockk {
                    every { system } returns CodeSystem.RONIN_FHIR_ID.uri
                    every { value } returns "123".asFHIR()
                }
            )
        }
        val mockMedicationRequest = mockk<MedicationRequest> {}
        every { JacksonUtil.readJsonObject("fake", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", Patient::class) } returns mockPatient
        val mockVendorFactory = mockk<VendorFactory> {
            every {
                medicationRequestService.getMedicationRequestByPatient(
                    tenant,
                    "123"
                )
            } returns listOf(mockMedicationRequest)
        }
        val request = destination.convertEventToRequest(
            "fake",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(
            ResourceRequestKey(
                "run123",
                ResourceType.Patient,
                tenant,
                "tenant-123"
            )
        )
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockMedicationRequest, results.first())
    }
}
