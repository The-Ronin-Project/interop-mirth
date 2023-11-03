package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.MedicationAdministrationService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.MedicationAdministration
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.util.asCode
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.OffsetDateTime

class MedicationAdministrationPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val medicationAdministrationService = mockk<MedicationAdministrationService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { medicationAdministrationService } returns this@MedicationAdministrationPublishTest.medicationAdministrationService
    }
    private val medicationAdministrationPublish =
        MedicationAdministrationPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))

    private val medicationRequest1 = MedicationRequest(
        id = Id("$tenantId-1234"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("Medication/$tenantId-1234"))
        ),
        intent = com.projectronin.interop.fhir.r4.valueset.MedicationRequestIntent.FILLER_ORDER.asCode(),
        status = com.projectronin.interop.fhir.r4.valueset.MedicationRequestStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationRequest2 = MedicationRequest(
        id = Id("$tenantId-5678"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("Medication/$tenantId-5678"))
        ),
        intent = com.projectronin.interop.fhir.r4.valueset.MedicationRequestIntent.FILLER_ORDER.asCode(),
        status = com.projectronin.interop.fhir.r4.valueset.MedicationRequestStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val medicationRequest3 = MedicationRequest(
        id = Id("$tenantId-9012"),
        medication = DynamicValue(
            DynamicValueType.REFERENCE,
            Reference(reference = FHIRString("SomethingElse/$tenantId-9012"))
        ),
        intent = com.projectronin.interop.fhir.r4.valueset.MedicationRequestIntent.FILLER_ORDER.asCode(),
        status = com.projectronin.interop.fhir.r4.valueset.MedicationRequestStatus.ACTIVE.asCode(),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )

    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
        every { backfillRequest } returns null
    }

    @Test
    fun `publish events create a PatientPublishMedicationAdministrationRequest for patient publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Patient
        }
        val request =
            medicationAdministrationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(
            MedicationAdministrationPublish.PatientPublishMedicationAdministrationRequest::class.java,
            request
        )
    }

    @Test
    fun `publish events create a MedicationRequestPublishMedicationAdministrationRequest for medication request publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.MedicationRequest
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(medicationRequest1)
            every { metadata } returns this@MedicationAdministrationPublishTest.metadata
        }
        val request =
            medicationAdministrationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(
            MedicationAdministrationPublish.MedicationRequestPublishMedicationAdministrationRequest::class.java,
            request
        )
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Practitioner
        }
        val exception = assertThrows<IllegalStateException> {
            medicationAdministrationPublish.convertPublishEventsToRequest(
                listOf(publishEvent),
                vendorFactory,
                tenant
            )
        }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load medication administrations",
            exception.message
        )
    }

    @Test
    fun `load events create a LoadMedicationAdministrationRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request =
            medicationAdministrationPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(MedicationAdministrationPublish.LoadMedicationAdministrationRequest::class.java, request)
    }

    @Test
    fun `PatientPublishMedicationAdministrationRequest supports load resources`() {
        val medicationAdministration1 = mockk<MedicationAdministration>()
        val medicationAdministration2 = mockk<MedicationAdministration>()
        val medicationAdministration3 = mockk<MedicationAdministration>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            medicationAdministrationService.findMedicationAdministrationsByPatient(
                tenant,
                "1234",
                LocalDate.now().minusMonths(2),
                LocalDate.now()
            )
        } returns listOf(
            medicationAdministration1,
            medicationAdministration2
        )
        every {
            medicationAdministrationService.findMedicationAdministrationsByPatient(
                tenant,
                "5678",
                LocalDate.now().minusMonths(2),
                LocalDate.now()
            )
        } returns listOf(
            medicationAdministration3
        )
        every {
            medicationAdministrationService.findMedicationAdministrationsByPatient(
                tenant,
                "9012",
                startDate.toLocalDate(),
                endDate.toLocalDate()
            )
        } returns emptyList()

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient3),
            metadata = Metadata(
                runId = "run",
                runDateTime = OffsetDateTime.now(),
                upstreamReferences = null,
                backfillRequest = Metadata.BackfillRequest(
                    backfillId = "123",
                    backfillStartDate = startDate,
                    backfillEndDate = endDate
                )
            )
        )
        val request =
            MedicationAdministrationPublish.PatientPublishMedicationAdministrationRequest(
                listOf(event1, event2, event3),
                medicationAdministrationService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(medicationAdministration1, medicationAdministration2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(medicationAdministration3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012", Pair(startDate, endDate))
        assertEquals(emptyList<MedicationAdministration>(), resourcesByKeys[key3])
    }

    @Test
    fun `MedicationRequestPublishMedicationAdministrationRequest supports load resources`() {
        val medicationAdministration1 = mockk<MedicationAdministration>()
        val medicationAdministration2 = mockk<MedicationAdministration>()
        val medicationAdministration3 = mockk<MedicationAdministration>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            medicationAdministrationService.findMedicationAdministrationsByRequest(tenant, medicationRequest1)
        } returns listOf(
            medicationAdministration1,
            medicationAdministration2
        )
        every {
            medicationAdministrationService.findMedicationAdministrationsByRequest(tenant, medicationRequest2)
        } returns listOf(
            medicationAdministration3
        )
        every {
            medicationAdministrationService.findMedicationAdministrationsByRequest(tenant, medicationRequest3)
        } returns emptyList()

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
            metadata = Metadata(
                runId = "run",
                runDateTime = OffsetDateTime.now(),
                upstreamReferences = null,
                backfillRequest = Metadata.BackfillRequest(
                    backfillId = "123",
                    backfillStartDate = startDate,
                    backfillEndDate = endDate
                )
            )
        )
        val request =
            MedicationAdministrationPublish.MedicationRequestPublishMedicationAdministrationRequest(
                listOf(event1, event2, event3),
                medicationAdministrationService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.MedicationRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(medicationAdministration1, medicationAdministration2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.MedicationRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(medicationAdministration3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey(
            "run",
            ResourceType.MedicationRequest,
            tenant,
            "$tenantId-9012",
            Pair(startDate, endDate)
        )
        assertEquals(emptyList<MedicationAdministration>(), resourcesByKeys[key3])
    }
}
