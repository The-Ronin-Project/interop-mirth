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
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channels.client.AidboxClient
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.ChannelMap
import com.projectronin.interop.mirth.channels.client.mirth.MEDICATION_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MEDICATION_STATEMENT_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime

class MedicationStatementLoadTest : BaseChannelTest(
    MEDICATION_STATEMENT_LOAD_CHANNEL_NAME,
    listOf("Patient", "MedicationStatement", "Medication"),
    listOf("Patient", "MedicationStatement", "Medication"),
) {
    private val patientType = "Patient"
    private val medicationStatementType = "MedicationStatement"
    private val medicationType = "Medication"

    private val medicationChannelId = ChannelMap.installedDag[MEDICATION_LOAD_CHANNEL_NAME]!!
    val metadata1 =
        Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now(),
            targetedResources = emptyList(),
        )

    @BeforeEach
    fun setupMedicationChannel() {
        MirthClient.clearChannelMessages(medicationChannelId)
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works`(testTenant: String) {
        tenantInUse = testTenant
        val fakePatient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }

        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$testTenant-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        val medicationStatement =
            medicationStatement {
                subject of reference(patientType, fakePatientId)
                status of "active"
                medication of DynamicValues.reference(reference("Medication", "1234"))
            }
        val medicationStatementId = MockEHRTestData.add(medicationStatement)
        MockOCIServerClient.createExpectations(medicationStatementType, medicationStatementId, tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
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

        val roninPatient1 =
            fakePatient1.copy(
                id = Id("$tenantInUse-$patient1Id"),
                identifier = fakePatient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id),
            )
        val roninPatient2 =
            fakePatient2.copy(
                id = Id("$tenantInUse-$patient2Id"),
                identifier = fakePatient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient2Id),
            )

        val fakeMedicationStatement1 =
            medicationStatement {
                subject of reference(patientType, patient1Id)
                status of "active"
                medication of DynamicValues.reference(reference("Medication", "1234"))
            }

        val fakeMedicationStatement2 =
            medicationStatement {
                subject of reference(patientType, patient2Id)
                status of "active"
                medication of DynamicValues.reference(reference("Medication", "1234"))
            }

        val medStatement1ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medStatement2ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medStatement3ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medStatement4ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medStatement5ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medStatement6ID = MockEHRTestData.add(fakeMedicationStatement1)
        val medStatement7ID = MockEHRTestData.add(fakeMedicationStatement2)
        MockOCIServerClient.createExpectations("MedicationStatement", medStatement1ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medStatement2ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medStatement3ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medStatement4ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medStatement5ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medStatement6ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationStatement", medStatement7ID, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2),
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(2, messageList.size)
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

        val fakeMedicationStatement1 =
            medicationStatement {
                subject of reference(patientType, patient1Id)
                status of "active"
                medication of DynamicValues.reference(reference("Medication", "1234"))
            }
        val fakeMedicationStatementId = MockEHRTestData.add(fakeMedicationStatement1)
        MockOCIServerClient.createExpectations("MedicationStatement", fakeMedicationStatementId, testTenant)

        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeMedicationStatementId),
            resourceType = ResourceType.MedicationStatement,
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        if (!tenantInUse.contains("cern")) {
            assertEquals(1, getAidboxResourceCount(medicationStatementType))
        }
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = TEST_TENANT,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.MedicationStatement,
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList, MirthResponseStatus.ERROR)
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount("MedicationStatement"))
    }

    @Test
    fun `channel works for MedicationStatements with contained Medications`() {
        tenantInUse = TEST_TENANT

        val fakePatient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }

        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$TEST_TENANT-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(TEST_TENANT) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        val containedMedication =
            medication {
                id of Id("13579")
                code of
                    codeableConcept {
                        text of "insulin regular (human) IV additive 100 units [1 units/hr] + sodium chloride 0.9% drip 100 mL"
                    }
            }

        val medicationStatement =
            medicationStatement {
                subject of reference(patientType, fakePatientId)
                status of "active"
                medication of DynamicValues.reference(Reference(reference = "#13579".asFHIR()))
                contained plus containedMedication
            }
        val medicationStatementId = MockEHRTestData.add(medicationStatement)
        MockOCIServerClient.createExpectations(medicationStatementType, medicationStatementId, tenantInUse)

        val medicationId = "contained-$medicationStatementId-13579"
        MockOCIServerClient.createExpectations(medicationType, medicationId, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(medicationStatementType))

        waitForMessage(2, channelID = medicationChannelId)
        val medicationMessageList = MirthClient.getChannelMessageIds(medicationChannelId)
        assertAllConnectorsStatus(medicationMessageList)
        assertEquals(2, medicationMessageList.size) // Also checks the Medication for child-references
        assertEquals(1, getAidboxResourceCount(medicationType))

        val storedMedicationStatement =
            AidboxClient.getResource<MedicationStatement>(
                medicationStatementType,
                "$tenantInUse-$medicationStatementId",
            )
        assertEquals(0, storedMedicationStatement.contained.size)
        assertEquals(
            "Medication/$tenantInUse-$medicationId",
            (storedMedicationStatement.medication?.value as? Reference)?.reference?.value,
        )

        val storedMedication =
            AidboxClient.getResource<Medication>(medicationType, "$tenantInUse-$medicationId")
        assertEquals(
            "insulin regular (human) IV additive 100 units [1 units/hr] + sodium chloride 0.9% drip 100 mL",
            storedMedication.code?.text?.value,
        )
    }

    @Test
    fun `channel works for MedicationStatements with codeable concept Medications`() {
        tenantInUse = TEST_TENANT

        val fakePatient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // required
                        },
                    )
                gender of "male"
            }

        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$TEST_TENANT-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(TEST_TENANT) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        val medicationCodeableConcept =
            codeableConcept {
                coding plus
                    coding {
                        system of "http://www.nlm.nih.gov/research/umls/rxnorm"
                        code of "161"
                        display of "acetaminophen"
                        userSelected of true
                    }
                text of "acetaminophen"
            }
        val medicationStatement =
            medicationStatement {
                subject of reference(patientType, fakePatientId)
                status of "active"
                medication of DynamicValues.codeableConcept(medicationCodeableConcept)
            }
        val medicationStatementId = MockEHRTestData.add(medicationStatement)
        MockOCIServerClient.createExpectations(medicationStatementType, medicationStatementId, tenantInUse)

        val medicationId = "codeable-$medicationStatementId-161"
        MockOCIServerClient.createExpectations(medicationType, medicationId, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(medicationStatementType))

        waitForMessage(2, channelID = medicationChannelId)
        val medicationMessageList = MirthClient.getChannelMessageIds(medicationChannelId)
        assertAllConnectorsStatus(medicationMessageList)
        assertEquals(2, medicationMessageList.size) // Also checks the Medication for child-references
        assertEquals(1, getAidboxResourceCount(medicationType))

        val storedMedicationStatement =
            AidboxClient.getResource<MedicationStatement>(
                medicationStatementType,
                "$tenantInUse-$medicationStatementId",
            )
        assertEquals(0, storedMedicationStatement.contained.size)
        assertEquals(
            "Medication/$tenantInUse-$medicationId",
            (storedMedicationStatement.medication?.value as? Reference)?.reference?.value,
        )

        val storedMedication =
            AidboxClient.getResource<Medication>(medicationType, "$tenantInUse-$medicationId")
        assertEquals(medicationCodeableConcept.coding.first().code, storedMedication.code!!.coding.first().code)
    }
}
