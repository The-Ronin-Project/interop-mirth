package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.MedicationRequestService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class MedicationRequestPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val medicationRequestService = mockk<MedicationRequestService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { medicationRequestService } returns this@MedicationRequestPublishTest.medicationRequestService
    }
    private val medicationRequestPublish = MedicationRequestPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))
    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
        every { backfillRequest } returns null
    }

    @Test
    fun `publish events create a PatientPublishMedicationRequestRequest`() {
        val publishEvent = mockk<InteropResourcePublishV1>()
        val request =
            medicationRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(MedicationRequestPublish.PatientPublishMedicationRequestRequest::class.java, request)
    }

    @Test
    fun `load events create a LoadMedicationRequestRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = medicationRequestPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(MedicationRequestPublish.LoadMedicationRequestRequest::class.java, request)
    }

    @Test
    fun `PatientPublishMedicationRequestRequest supports loads resources`() {
        val medicationRequest1 = mockk<MedicationRequest>()
        val medicationRequest2 = mockk<MedicationRequest>()
        val medicationRequest3 = mockk<MedicationRequest>()
        every { medicationRequestService.getMedicationRequestByPatient(tenant, "1234") } returns listOf(
            medicationRequest1,
            medicationRequest2
        )
        every { medicationRequestService.getMedicationRequestByPatient(tenant, "5678") } returns listOf(
            medicationRequest3
        )
        every { medicationRequestService.getMedicationRequestByPatient(tenant, "9012") } returns emptyList()

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
            metadata = metadata
        )
        val request =
            MedicationRequestPublish.PatientPublishMedicationRequestRequest(
                listOf(event1, event2, event3),
                medicationRequestService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(medicationRequest1, medicationRequest2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(medicationRequest3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012")
        assertEquals(emptyList<MedicationRequest>(), resourcesByKeys[key3])
    }
}
