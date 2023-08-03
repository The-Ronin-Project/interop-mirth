package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.ObservationService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.ConditionStage
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ObservationPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val observationService = mockk<ObservationService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { observationService } returns this@ObservationPublishTest.observationService
    }
    private val observationPublish = ObservationPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))

    private val condition1 = Condition(
        id = Id("$tenantId-1234"),
        stage = listOf(
            ConditionStage(
                assessment = listOf(
                    Reference(reference = FHIRString("Observation/$tenantId-1234")),
                    Reference(reference = FHIRString("Observation/$tenantId-5678"))
                )
            )
        ),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val condition2 = Condition(
        id = Id("$tenantId-5678"),
        stage = listOf(
            ConditionStage(
                assessment = listOf(
                    Reference(reference = FHIRString("Observation/$tenantId-9012"))
                )
            ),
            ConditionStage(
                assessment = listOf(
                    Reference(reference = FHIRString("Observation/$tenantId-3456"))
                )
            )
        ),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )
    private val condition3 = Condition(
        id = Id("$tenantId-5678"),
        stage = listOf(
            ConditionStage(
                assessment = listOf(
                    Reference(reference = FHIRString("Other/$tenantId-7890"))
                )
            )
        ),
        subject = Reference(reference = FHIRString("Patient/$tenantId-1234"))
    )

    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
    }

    @Test
    fun `publish events create a PatientPublishObservationRequest for patient publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Patient
        }
        val request = observationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ObservationPublish.PatientPublishObservationRequest::class.java, request)
    }

    @Test
    fun `publish events create a ConditionPublishObservationRequest for condition publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Condition
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(condition1)
            every { metadata } returns this@ObservationPublishTest.metadata
        }
        val request = observationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ObservationPublish.ConditionPublishObservationRequest::class.java, request)
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1>(relaxed = true) {
            every { resourceType } returns ResourceType.Practitioner
        }
        val exception = assertThrows<IllegalStateException> {
            observationPublish.convertPublishEventsToRequest(
                listOf(publishEvent),
                vendorFactory,
                tenant
            )
        }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load observations",
            exception.message
        )
    }

    @Test
    fun `load events create a LoadObservationRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = observationPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(ObservationPublish.LoadObservationRequest::class.java, request)
    }

    @Test
    fun `PatientPublishObservationRequest supports loads resources`() {
        val categories = listOf(
            FHIRSearchToken(CodeSystem.OBSERVATION_CATEGORY.uri.value, ObservationCategoryCodes.VITAL_SIGNS.code),
            FHIRSearchToken(CodeSystem.OBSERVATION_CATEGORY.uri.value, ObservationCategoryCodes.LABORATORY.code)
        )

        val observation1 = mockk<Observation>()
        val observation2 = mockk<Observation>()
        val observation3 = mockk<Observation>()
        every {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("1234"),
                categories
            )
        } returns listOf(
            observation1,
            observation2
        )
        every {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("5678"),
                categories
            )
        } returns listOf(observation3)
        every {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("9012"),
                categories
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
            metadata = metadata
        )
        val request =
            ObservationPublish.PatientPublishObservationRequest(
                listOf(event1, event2, event3),
                observationService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(observation1, observation2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(observation3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012")
        assertEquals(emptyList<Observation>(), resourcesByKeys[key3])
    }

    @Test
    fun `ConditionPublishObservationRequest supports loads resources`() {
        val observation1 = mockk<Observation>()
        val observation2 = mockk<Observation>()
        val observation3 = mockk<Observation>()
        val observation4 = mockk<Observation>()
        every {
            observationService.getByIDs(
                tenant,
                listOf("1234", "5678", "9012", "3456")
            )
        } returns mapOf("1234" to observation1, "5678" to observation2, "9012" to observation3, "3456" to observation4)

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Condition,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(condition1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Condition,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(condition2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Condition,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(condition3),
            metadata = metadata
        )
        val request =
            ObservationPublish.ConditionPublishObservationRequest(
                listOf(event1, event2, event3),
                observationService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(4, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Observation, tenant, "$tenantId-1234")
        assertEquals(listOf(observation1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Observation, tenant, "$tenantId-5678")
        assertEquals(listOf(observation2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Observation, tenant, "$tenantId-9012")
        assertEquals(listOf(observation3), resourcesByKeys[key3])

        val key4 = ResourceRequestKey("run", ResourceType.Observation, tenant, "$tenantId-3456")
        assertEquals(listOf(observation4), resourcesByKeys[key4])
    }
}
