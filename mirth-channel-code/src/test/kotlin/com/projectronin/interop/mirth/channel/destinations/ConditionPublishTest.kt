package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.ConditionService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class ConditionPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val conditionService = mockk<ConditionService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { conditionService } returns this@ConditionPublishTest.conditionService
    }
    private val conditionPublish = ConditionPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))
    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
        every { backfillRequest } returns null
    }

    @Test
    fun `publish events create a PatientPublishConditionRequest`() {
        val publishEvent = mockk<InteropResourcePublishV1>()
        val request = conditionPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ConditionPublish.PatientPublishConditionRequest::class.java, request)
    }

    @Test
    fun `load events create a LoadConditionRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = conditionPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(ConditionPublish.LoadConditionRequest::class.java, request)
    }

    @Test
    fun `PatientPublishConditionRequest supports loads resources`() {
        val categories = listOf(
            FHIRSearchToken(CodeSystem.CONDITION_CATEGORY.uri.value, ConditionCategoryCodes.PROBLEM_LIST_ITEM.code),
            FHIRSearchToken(
                CodeSystem.CONDITION_CATEGORY_HEALTH_CONCERN.uri.value,
                ConditionCategoryCodes.HEALTH_CONCERN.code
            ),
            FHIRSearchToken(CodeSystem.CONDITION_CATEGORY.uri.value, ConditionCategoryCodes.ENCOUNTER_DIAGNOSIS.code)
        )

        val condition1 = mockk<Condition>()
        val condition2 = mockk<Condition>()
        val condition3 = mockk<Condition>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every { conditionService.findConditionsByCodes(tenant, "1234", categories) } returns listOf(
            condition1,
            condition2
        )
        every { conditionService.findConditionsByCodes(tenant, "5678", categories) } returns listOf(condition3)
        every { conditionService.findConditionsByCodes(tenant, "9012", categories) } returns emptyList()

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
                    backfillID = "123",
                    backfillStartDate = startDate,
                    backfillEndDate = endDate
                )
            )
        )
        val request =
            ConditionPublish.PatientPublishConditionRequest(
                listOf(event1, event2, event3),
                conditionService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(condition1, condition2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(condition3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012", Pair(startDate, endDate))
        assertEquals(emptyList<Condition>(), resourcesByKeys[key3])
    }
}
