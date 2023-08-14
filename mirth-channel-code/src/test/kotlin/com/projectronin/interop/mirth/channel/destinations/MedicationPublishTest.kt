package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.MedicationService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Ingredient
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.valueset.MedicationRequestIntent
import com.projectronin.interop.fhir.r4.valueset.MedicationRequestStatus
import com.projectronin.interop.fhir.r4.valueset.MedicationStatementStatus
import com.projectronin.interop.fhir.util.asCode
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MedicationPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val medicationService = mockk<MedicationService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { medicationService } returns this@MedicationPublishTest.medicationService
    }
    private val medicationPublish = MedicationPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val medication1 = Medication(
        id = Id("$tenantId-1234"),
        ingredient = listOf(
            Ingredient(
                item = DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("Medication/$tenantId-11234"))
                )
            ),
            Ingredient(
                item = DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("Medication/$tenantId-15678"))
                )
            )
        )
    )
    private val medication2 = Medication(
        id = Id("$tenantId-5678"),
        ingredient = listOf(
            Ingredient(
                item = DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("Medication/$tenantId-19012"))
                )
            ),
            Ingredient(
                item = DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("SomethingElse/$tenantId-13456"))
                )
            )
        )
    )
    private val medication3 = Medication(
        id = Id("$tenantId-9012"),
        ingredient = listOf(
            Ingredient(item = DynamicValue(DynamicValueType.STRING, FHIRString("Medication")))
        )
    )

    private val medicationRequest1 = MedicationRequest(
        id = Id("$tenantId-1234"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("Medication/$tenantId-1234"))
        ),
        intent = MedicationRequestIntent.FILLER_ORDER.asCode(),
        status = MedicationRequestStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationRequest2 = MedicationRequest(
        id = Id("$tenantId-5678"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("Medication/$tenantId-5678"))
        ),
        intent = MedicationRequestIntent.FILLER_ORDER.asCode(),
        status = MedicationRequestStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationRequest3 = MedicationRequest(
        id = Id("$tenantId-9012"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("SomethingElse/$tenantId-9012"))
        ),
        intent = MedicationRequestIntent.FILLER_ORDER.asCode(),
        status = MedicationRequestStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationRequest4 = MedicationRequest(
        id = Id("$tenantId-3456"),
        medication = DynamicValue(
            DynamicValueType.STRING,
            FHIRString("Medication")
        ),
        intent = MedicationRequestIntent.FILLER_ORDER.asCode(),
        status = MedicationRequestStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )

    private val medicationStatement1 = MedicationStatement(
        id = Id("$tenantId-1234"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("Medication/$tenantId-1234"))
        ),
        status = MedicationStatementStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationStatement2 = MedicationStatement(
        id = Id("$tenantId-5678"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("Medication/$tenantId-5678"))
        ),
        status = MedicationStatementStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationStatement3 = MedicationStatement(
        id = Id("$tenantId-9012"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("SomethingElse/$tenantId-9012"))
        ),
        status = MedicationStatementStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationStatement4 = MedicationStatement(
        id = Id("$tenantId-3456"),
        medication = DynamicValue(
            DynamicValueType.STRING,
            FHIRString("Medication")
        ),
        status = MedicationStatementStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )

    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
    }

    @Test
    fun `publish events create a MedicationPublishMedicationRequest for medication publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Medication
        }
        val request = medicationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(MedicationPublish.MedicationPublishMedicationRequest::class.java, request)
    }

    @Test
    fun `publish events create a MedicationRequestPublishMedicationRequest for medicationRequest publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.MedicationRequest
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(medicationRequest1)
            every { metadata } returns this@MedicationPublishTest.metadata
        }
        val request = medicationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(MedicationPublish.MedicationRequestPublishMedicationRequest::class.java, request)
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Practitioner
        }
        val exception = assertThrows<IllegalStateException> {
            medicationPublish.convertPublishEventsToRequest(
                listOf(publishEvent),
                vendorFactory,
                tenant
            )
        }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load medications",
            exception.message
        )
    }

    @Test
    fun `load events create a LoadMedicationRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = medicationPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(MedicationPublish.LoadMedicationRequest::class.java, request)
    }

    @Test
    fun `MedicationPublishMedicationRequest supports loads resources`() {
        val returnedMedication1 = mockk<Medication>()
        val returnedMedication2 = mockk<Medication>()
        val returnedMedication3 = mockk<Medication>()
        every { medicationService.getByIDs(tenant, listOf("11234", "15678", "19012")) } returns mapOf(
            "11234" to returnedMedication1,
            "15678" to returnedMedication2,
            "19012" to returnedMedication3
        )

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Medication,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medication1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Medication,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medication2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Medication,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medication3),
            metadata = metadata
        )
        val request =
            MedicationPublish.MedicationPublishMedicationRequest(
                listOf(event1, event2, event3),
                medicationService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Medication, tenant, "$tenantId-11234")
        assertEquals(listOf(returnedMedication1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Medication, tenant, "$tenantId-15678")
        assertEquals(listOf(returnedMedication2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Medication, tenant, "$tenantId-19012")
        assertEquals(listOf(returnedMedication3), resourcesByKeys[key3])
    }

    @Test
    fun `MedicationRequestPublishMedicationRequest supports loads resources`() {
        val medication1 = mockk<Medication>()
        val medication2 = mockk<Medication>()
        every {
            medicationService.getByIDs(
                tenant,
                listOf("1234", "5678")
            )
        } returns mapOf("1234" to medication1, "5678" to medication2)

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationRequest,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationRequest1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationRequest,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationRequest2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationRequest,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationRequest3),
            metadata = metadata
        )
        val event4 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationRequest,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationRequest4),
            metadata = metadata
        )
        val request =
            MedicationPublish.MedicationRequestPublishMedicationRequest(
                listOf(event1, event2, event3, event4),
                medicationService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(2, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Medication, tenant, "$tenantId-1234")
        assertEquals(listOf(medication1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Medication, tenant, "$tenantId-5678")
        assertEquals(listOf(medication2), resourcesByKeys[key2])
    }

    @Test
    fun `publish events create a MedicationStatementPublishMedicationRequest for medicationStatement publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.MedicationStatement
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(medicationStatement1)
            every { metadata } returns this@MedicationPublishTest.metadata
        }
        val request = medicationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(MedicationPublish.MedicationStatementPublishMedicationRequest::class.java, request)
    }

    @Test
    fun `MedicationStatementPublishMedicationRequest supports loads resources`() {
        val medication1 = mockk<Medication>()
        val medication2 = mockk<Medication>()
        every {
            medicationService.getByIDs(
                tenant,
                listOf("1234", "5678")
            )
        } returns mapOf("1234" to medication1, "5678" to medication2)

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationStatement,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationStatement1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationStatement,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationStatement2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationStatement,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationStatement3),
            metadata = metadata
        )
        val event4 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.MedicationStatement,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationStatement4),
            metadata = metadata
        )
        val request =
            MedicationPublish.MedicationStatementPublishMedicationRequest(
                listOf(event1, event2, event3, event4),
                medicationService,
                tenant
            )
        val resourcesByKeys =
            request.loadResources(request.requestKeys.toList())
        assertEquals(2, resourcesByKeys.size)

        val key1 = ResourceRequestKey(
            "run",
            ResourceType.Medication,
            tenant,
            "$tenantId-1234"
        )
        assertEquals(listOf(medication1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey(
            "run",
            ResourceType.Medication,
            tenant,
            "$tenantId-5678"
        )
        assertEquals(listOf(medication2), resourcesByKeys[key2])
    }
}
