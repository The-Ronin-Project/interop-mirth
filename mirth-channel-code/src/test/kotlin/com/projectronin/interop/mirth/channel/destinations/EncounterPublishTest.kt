package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.EncounterService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class EncounterPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val encounterService = mockk<EncounterService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { encounterService } returns this@EncounterPublishTest.encounterService
    }
    private val encounterPublish = EncounterPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))
    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
        every { backfillRequest } returns null
    }

    @Test
    fun `publish events create a PatientPublishEncounterRequest`() {
        val publishEvent = mockk<InteropResourcePublishV1>()
        val request = encounterPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(EncounterPublish.PatientPublishEncounterRequest::class.java, request)
    }

    @Test
    fun `load events create a LoadEncounterRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = encounterPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(EncounterPublish.LoadEncounterRequest::class.java, request)
    }

    @Test
    fun `PatientPublishEncounterRequest supports loads resources`() {
        val encounter1 = mockk<Encounter>()
        val encounter2 = mockk<Encounter>()
        val encounter3 = mockk<Encounter>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every { encounterService.findPatientEncounters(tenant, "1234", any(), any()) } returns listOf(
            encounter1,
            encounter2
        )
        every { encounterService.findPatientEncounters(tenant, "5678", any(), any()) } returns listOf(encounter3)
        every { encounterService.findPatientEncounters(tenant, "9012", startDate.toLocalDate(), endDate.toLocalDate()) } returns emptyList()

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
            EncounterPublish.PatientPublishEncounterRequest(
                listOf(event1, event2, event3),
                encounterService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(encounter1, encounter2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(encounter3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012", Pair(startDate, endDate))
        assertEquals(emptyList<Encounter>(), resourcesByKeys[key3])
    }
}
