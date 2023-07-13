package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Ingredient
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
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

class MedicationPublishTest {
    lateinit var tenant: Tenant
    lateinit var destination: MedicationPublish

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        destination = MedicationPublish(mockk(), mockk(), mockk(), mockk(), mockk())
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
    fun `fails on unknown resource type`() {
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.Location,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            mockk()
        )

        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        assertThrows<IllegalStateException> {
            destination.convertEventToRequest("boo", InteropResourcePublishV1::class.simpleName!!, mockk(), mockk())
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
            ResourceType.Medication,
            InteropResourceLoadV1.DataTrigger.adhoc,
            metadata
        )
        val mockMedication = mockk<Medication>()
        every { JacksonUtil.readJsonObject("boo", InteropResourceLoadV1::class) } returns event
        val mockVendorFactory = mockk<VendorFactory> {
            every { medicationService.getByID(tenant, "id") } returns mockMedication
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourceLoadV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(ResourceRequestKey("run123", ResourceType.Medication, tenant, "id"))
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockMedication, results.first())
    }

    @Test
    fun `works for publish events - medication request`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.MedicationRequest,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockkMedicationRequest = mockk<MedicationRequest> {
            every { id?.value } returns "tenant-123"
            every { medication } returns mockk<DynamicValue<Reference>> {
                every { type } returns DynamicValueType.REFERENCE
                every { value } returns Reference(reference = "Medication/tenant-456".asFHIR())
            }
        }

        val mockMedication = mockk<Medication> {}
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", MedicationRequest::class) } returns mockkMedicationRequest
        val mockVendorFactory = mockk<VendorFactory> {
            every {
                medicationService.getByID(
                    tenant,
                    "456"
                )
            } returns mockMedication
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(ResourceRequestKey("run123", ResourceType.Medication, tenant, "tenant-456"))
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockMedication, results.first())
    }

    @Test
    fun `works for publish events - medication`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.Medication,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockkMedicationSource = mockk<Medication> {
            every { id?.value } returns "tenant-123"
            every { ingredient } returns listOf(
                mockk<Ingredient> {
                    every { item } returns mockk<DynamicValue<Reference>> {
                        every { type } returns DynamicValueType.REFERENCE
                        every { value } returns Reference(reference = "Medication/tenant-456".asFHIR())
                    }
                },
                mockk<Ingredient> {
                    every { item } returns mockk<DynamicValue<Boolean>> {
                        every { type } returns DynamicValueType.BOOLEAN
                    }
                },
                mockk<Ingredient> {
                    every { item } returns mockk<DynamicValue<Reference>> {
                        every { type } returns DynamicValueType.REFERENCE
                        every { value } returns Reference(reference = "Location/tenant-456".asFHIR())
                    }
                }
            )
        }

        val mockMedication = mockk<Medication> {}
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", Medication::class) } returns mockkMedicationSource
        val mockVendorFactory = mockk<VendorFactory> {
            every {
                medicationService.getByID(
                    tenant,
                    "456"
                )
            } returns mockMedication
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(ResourceRequestKey("run123", ResourceType.Medication, tenant, "tenant-456"))
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockMedication, results.first())
    }

    @Test
    fun `no reference means no keys`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.MedicationRequest,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockkMedicationRequest = mockk<MedicationRequest> {
            every { id?.value } returns "tenant-123"
            every { medication } returns mockk<DynamicValue<Reference>> {
                every { type } returns DynamicValueType.REFERENCE
                every { value } returns Reference(reference = "NotMedication/tenant-456".asFHIR())
            }
        }

        val mockMedication = mockk<Medication> {}
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", MedicationRequest::class) } returns mockkMedicationRequest
        val mockVendorFactory = mockk<VendorFactory> {
            every {
                medicationService.getByID(
                    tenant,
                    "456"
                )
            } returns mockMedication
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        assertEquals(emptyList<ResourceRequestKey>(), request.requestKeys)
    }

    @Test
    fun `no reference means no keys- medication`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.Medication,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockkMedicationSource = mockk<Medication> {
            every { id?.value } returns "tenant-123"
            every { ingredient } returns emptyList()
        }

        val mockMedication = mockk<Medication> {}
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", Medication::class) } returns mockkMedicationSource
        val mockVendorFactory = mockk<VendorFactory> {
            every {
                medicationService.getByID(
                    tenant,
                    "456"
                )
            } returns mockMedication
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        assertEquals(emptyList<ResourceRequestKey>(), request.requestKeys)
    }

    @Test
    fun `not a reference means no keys`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.MedicationRequest,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockkMedicationRequest = mockk<MedicationRequest> {
            every { id?.value } returns "tenant-123"
            every { medication } returns mockk<DynamicValue<Reference>> {
                every { type } returns DynamicValueType.DATE
            }
        }

        val mockMedication = mockk<Medication> {}
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", MedicationRequest::class) } returns mockkMedicationRequest
        val mockVendorFactory = mockk<VendorFactory> {
            every {
                medicationService.getByID(
                    tenant,
                    "456"
                )
            } returns mockMedication
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        assertEquals(emptyList<ResourceRequestKey>(), request.requestKeys)
    }
}
