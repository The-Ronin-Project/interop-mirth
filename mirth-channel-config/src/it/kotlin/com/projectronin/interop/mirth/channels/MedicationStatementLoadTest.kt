package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.medication
import com.projectronin.interop.fhir.generators.resources.medicationStatement
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

const val medicationStatementLoadChannelName = "MedicationStatementLoad"

class MedicationStatementLoadTest : BaseChannelTest(
    medicationStatementLoadChannelName,
    listOf("Patient", "MedicationStatement", "Medication"),
    listOf("Patient", "MedicationStatement", "Medication")
) {
    val patientType = "Patient"
    val medicationStatementType = "MedicationStatement"

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works`(testTenant: String) {
        tenantInUse = testTenant
        val fakePatient = patient {
            birthDate of date {
                year of 1990
                month of 1
                day of 3
            }
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
                    value of "1000000001"
                }
            )
            name of listOf(
                name {
                    use of "usual" // required
                }
            )
            gender of "male"
        }

        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$testTenant-$fakePatientId"
        val fakeAidboxPatient = fakePatient.copy(
            id = Id(fakeAidboxPatientId),
            identifier = fakePatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(fakePatientId)
        )
        AidboxTestData.add(fakeAidboxPatient)

        val medicationStatement = medicationStatement {
            subject of reference(patientType, fakePatientId)
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }
        val medicationStatementId = MockEHRTestData.add(medicationStatement)
        MockOCIServerClient.createExpectations(medicationStatementType, medicationStatementId)
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient)
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        if (!tenantInUse.contains("cern")) {
            assertEquals(1, getAidboxResourceCount(medicationStatementType))
        }
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and medication requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakePatient1 = patient {}
        val patient1Id = MockEHRTestData.add(fakePatient1)

        val fakePatient2 = patient {}
        val patient2Id = MockEHRTestData.add(fakePatient2)

        val roninPatient1 = fakePatient1.copy(
            id = Id("$tenantInUse-$patient1Id"),
            identifier = fakePatient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )
        val roninPatient2 = fakePatient2.copy(
            id = Id("$tenantInUse-$patient2Id"),
            identifier = fakePatient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient2Id)
        )

        val fakeMedicationStatement1 = medicationStatement {
            subject of reference(patientType, patient1Id)
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }

        val fakeMedicationStatement2 = medicationStatement {
            subject of reference(patientType, patient2Id)
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }

        val medRequest1ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medRequest2ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medRequest3ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medRequest4ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medRequest5ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medRequest6ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medRequest7ID = MockEHRTestData.add(fakeMedicationStatement2)
        MockOCIServerClient.createExpectations("MedicationStatement", medRequest1ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medRequest2ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medRequest3ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medRequest4ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medRequest5ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medRequest6ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medRequest7ID, tenantInUse)
        MockEHRTestData.validateAll()

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2)
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        if (!tenantInUse.contains("cern")) {
            assertEquals(7, getAidboxResourceCount(medicationStatementType))
        }
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakePatient1 = patient {}
        val patient1Id = MockEHRTestData.add(fakePatient1)

        val fakeMedicationStatement1 = medicationStatement {
            subject of reference(patientType, patient1Id)
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }
        val fakeMedicationStatementId = MockEHRTestData.add(fakeMedicationStatement1)
        MockOCIServerClient.createExpectations("MedicationStatement", fakeMedicationStatementId, testTenant)

        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeMedicationStatementId),
            resourceType = ResourceType.MedicationStatement
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        if (!tenantInUse.contains("cern")) {
            assertEquals(1, getAidboxResourceCount(medicationStatementType))
        }
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.MedicationStatement
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsError(messageList)
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount("MedicationStatement"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag`(testTenant: String) {
        tenantInUse = testTenant
        if (tenantInUse.contains("cern")) return

        val medicationStatementPublishTopics = KafkaClient.publishTopics(ResourceType.MedicationStatement)
        val medicationStatementType = "MedicationStatement"
        val medicationType = "Medication"
        val types = listOf(
            medicationStatementType,
            medicationType
        )

        val channels = listOf(
            medicationLoadChannelName
        )
        val channelIds = channels.map {
            installChannel(it)
        }

        val fakeMedication = medication {
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of "ok"
                        code of "yeah"
                    }
                )
            }
        }
        val fakeMedicationId = MockEHRTestData.add(fakeMedication)
        MockOCIServerClient.createExpectations(medicationType, fakeMedicationId, testTenant)

        val fakeMedicationStatement = medicationStatement {
            id of "123"
            status of "active"
            medication of DynamicValues.reference(reference(medicationType, fakeMedicationId))
            subject of reference(patientType, "asdadsdas")
        }

        val medicationStatementId = MockEHRTestData.add(fakeMedicationStatement)
        MockOCIServerClient.createExpectations(medicationStatementType, medicationStatementId, testTenant)

        // deploy dag channels
        channelIds.forEach {
            deployAndStartChannel(channelToDeploy = it)
            clearMessages(it)
        }
        medicationStatementPublishTopics.forEach {
            KafkaClient.ensureStability(it.topicName)
        }
        runBlocking { delay(1000) }
        // push event to get picked up
        val metadata = Metadata(runId = UUID.randomUUID().toString(), runDateTime = OffsetDateTime.now(ZoneOffset.UTC))
        KafkaClient.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(medicationStatementId),
            ResourceType.MedicationStatement,
            metadata
        )
        val medicationStatementPublishTopic =
            KafkaClient.publishTopics(ResourceType.MedicationStatement).first { it.topicName.contains("nightly") }
        KafkaClient.ensureStability(medicationStatementPublishTopic.topicName)
        waitForMessage(1)
        channelIds.forEach {
            waitForMessage(2, channelID = it)
            stopChannel(it)
        }
        types.forEach {
            assertEquals(1, getAidboxResourceCount(it))
        }
    }
}
