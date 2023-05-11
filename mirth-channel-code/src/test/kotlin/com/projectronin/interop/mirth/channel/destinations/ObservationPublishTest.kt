package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
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

class ObservationPublishTest {
    lateinit var tenant: Tenant
    lateinit var destination: ObservationPublish

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        destination = ObservationPublish(mockk(), mockk(), mockk(), mockk(), mockk())
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
            destination.convertEventToRequest("boo", "", mockk(), mockk())
        }
    }

    @Test
    fun `works for load events`() {
        val metadata = mockk<Metadata>()
        val event = InteropResourceLoadV1(
            "tenant",
            "id",
            ResourceType.Observation,
            InteropResourceLoadV1.DataTrigger.adhoc,
            metadata
        )
        val mockObservation = mockk<Observation>()
        every { JacksonUtil.readJsonObject("boo", InteropResourceLoadV1::class) } returns event
        val mockVendorFactory = mockk<VendorFactory> {
            every { observationService.getByID(tenant, "id") } returns mockObservation
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourceLoadV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        val results = request.loadResources()
        assertEquals(mockObservation, results.first())
    }

    @Test
    fun `works for publish patient events`() {
        val metadata = mockk<Metadata>()
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.Patient,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockPatient = mockk<Patient> {
            every { identifier } returns listOf(
                mockk {
                    every { system } returns CodeSystem.RONIN_FHIR_ID.uri
                    every { value } returns "123".asFHIR()
                }
            )
        }
        val mockObservation = mockk<Observation> {}
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", Patient::class) } returns mockPatient
        val mockVendorFactory = mockk<VendorFactory> {
            every { observationService.findObservationsByPatientAndCategory(tenant, listOf("123"), any()) } returns
                listOf(mockObservation)
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        val results = request.loadResources()
        assertEquals(mockObservation, results.first())
    }

    @Test
    fun `works for publish condition events`() {
        val metadata = mockk<Metadata>()
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.Condition,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockCondition = mockk<Condition> {
            every { stage } returns listOf(
                mockk {
                    every { assessment } returns listOf(
                        mockk {
                            every { isForType("Observation") } returns true
                            every { decomposedId() } returns "123"
                        }
                    )
                }
            )
        }
        val mockObservation = mockk<Observation>()
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", Condition::class) } returns mockCondition

        val mockVendorFactory = mockk<VendorFactory> {
            every { observationService } returns mockk {
                every { fhirResourceType } returns Observation::class.java
                every { getByID(tenant, "123") } returns mockObservation
            }
        }

        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        val results = request.loadResources()
        assertEquals(mockObservation, results.first())
    }

    @Test
    fun `fails on unknown publish request`() {
        val metadata = mockk<Metadata>()
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.MedicationRequest,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event

        assertThrows<IllegalStateException> {
            destination.convertEventToRequest(
                "boo",
                InteropResourcePublishV1::class.simpleName!!,
                mockk(),
                mockk()
            )
        }
    }
}
