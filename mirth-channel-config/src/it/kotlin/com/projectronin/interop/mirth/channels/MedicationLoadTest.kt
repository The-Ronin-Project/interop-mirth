package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.ingredient
import com.projectronin.interop.fhir.generators.resources.medication
import com.projectronin.interop.fhir.generators.resources.medicationRequest
import com.projectronin.interop.fhir.generators.resources.medicationStatement
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.mirth.MEDICATION_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime

class MedicationLoadTest : BaseChannelTest(
    MEDICATION_LOAD_CHANNEL_NAME,
    listOf("Medication"),
    listOf("Medication"),
) {
    val metadata1 =
        Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now(),
            targetedResources = emptyList(),
        )

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works nightly from medication requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakeMedication =
            medication {
                code of
                    codeableConcept {
                        text of "Example medication"
                    }
            }
        val fakeMedicationId = MockEHRTestData.add(fakeMedication)

        val fakeMedicationRequest1 =
            medicationRequest {
                id of "123"
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of DynamicValues.reference(reference("Medication", fakeMedicationId))
            }

        MockOCIServerClient.createExpectations("Medication", fakeMedicationId, tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeMedicationRequest1),
            metadata = metadata1,
        )

        waitForMessage(2)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(2, messageList.size)
        assertEquals(1, getAidboxResourceCount("Medication"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works nightly for medication sources`(testTenant: String) {
        tenantInUse = testTenant

        val fakeMedication =
            medication {
                code of
                    codeableConcept {
                        text of "Example medication"
                    }
            }
        val fakeMedicationId = MockEHRTestData.add(fakeMedication)

        val fakeMedication2 =
            medication {
                code of
                    codeableConcept {
                        text of "Example medication"
                    }
                ingredient of
                    listOf(
                        ingredient {
                            item of DynamicValues.reference(reference("Medication", fakeMedicationId))
                        },
                    )
            }
        val fakeMedication2ID = MockEHRTestData.add(fakeMedication2)

        MockOCIServerClient.createExpectations("Medication", fakeMedicationId, tenantInUse)
        MockOCIServerClient.createExpectations("Medication", fakeMedication2ID, tenantInUse)
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeMedication2ID),
            resourceType = ResourceType.Medication,
            metadata = metadata1,
        )

        waitForMessage(2)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(2, messageList.size)
        assertEquals(2, getAidboxResourceCount("Medication"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakeMedication =
            medication {
                code of
                    codeableConcept {
                        text of "Example medication"
                    }
            }
        val fakeMedicationId = MockEHRTestData.add(fakeMedication)
        MockOCIServerClient.createExpectations("Medication", fakeMedicationId, testTenant)

        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeMedicationId),
            resourceType = ResourceType.Medication,
            metadata = metadata1,
        )
        waitForMessage(2)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(2, messageList.size)
        assertEquals(1, getAidboxResourceCount("Medication"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works nightly from medication statements`(testTenant: String) {
        tenantInUse = testTenant

        val fakeMedication =
            medication {
                code of
                    codeableConcept {
                        text of "Example medication"
                    }
            }
        val fakeMedicationId = MockEHRTestData.add(fakeMedication)

        val fakeMedicationStatement1 =
            medicationStatement {
                id of "123"
                status of "active"
                medication of DynamicValues.reference(reference("Medication", fakeMedicationId))
            }

        MockOCIServerClient.createExpectations("Medication", fakeMedicationId, tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeMedicationStatement1),
            metadata = metadata1,
        )

        waitForMessage(2)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(2, messageList.size)
        assertEquals(1, getAidboxResourceCount("Medication"))
    }
}
