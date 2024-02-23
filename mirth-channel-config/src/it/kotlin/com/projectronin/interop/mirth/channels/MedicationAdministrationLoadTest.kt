package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.quantity
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.dateTime
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.encounter
import com.projectronin.interop.fhir.generators.resources.medAdminDosage
import com.projectronin.interop.fhir.generators.resources.medication
import com.projectronin.interop.fhir.generators.resources.medicationAdministration
import com.projectronin.interop.fhir.generators.resources.medicationRequest
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationAdministration
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxClient
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.ChannelMap
import com.projectronin.interop.mirth.channels.client.mirth.MEDICATION_ADMIN_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MEDICATION_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MedicationAdministrationLoadTest : BaseChannelTest(
    MEDICATION_ADMIN_LOAD_CHANNEL_NAME,
    listOf("Patient", "MedicationAdministration", "MedicationRequest", "Medication", "Encounter"),
    listOf("Patient", "MedicationAdministration", "MedicationRequest", "Medication", "Encounter"),
) {
    private val patientType = "Patient"
    private val encounterType = "Encounter"
    private val medicationAdministrationType = "MedicationAdministration"
    private val medicationRequestType = "MedicationRequest"
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
    fun `channel works for patients`(testTenant: String) {
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
        val fakeAidboxPatientId = "$tenantInUse-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val medicationAdministration =
            medicationAdministration {
                subject of reference(patientType, fakePatientId)
                medication of DynamicValues.reference(reference(medicationType, "1234"))
                effective of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
            }
        val medicationAdministrationId = MockEHRTestData.add(medicationAdministration)
        MockOCIServerClient.createExpectations(medicationAdministrationType, medicationAdministrationId, tenantInUse)
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

        val expectedAidboxResourceCount = if (testTenant == "epicmock") 0 else 1 // Epic does not load for Patients
        assertEquals(expectedAidboxResourceCount, getAidboxResourceCount(medicationAdministrationType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for medication requests`(testTenant: String) {
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
                            type of
                                codeableConcept {
                                    text of "Internal"
                                }
                            system of "mockPatientInternalSystem"
                            value of "InternalPatientId"
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
        val fakeAidboxPatientId = "$tenantInUse-$fakePatientId"
        val fakeAidboxPatient =
            fakePatient.copy(
                id = Id(fakeAidboxPatientId),
                identifier = fakePatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(fakePatientId),
            )
        AidboxTestData.add(fakeAidboxPatient)

        val fakeEncounter =
            encounter {
                `class` of
                    coding {
                        system of "urn:oid:1.2.840.114350.1.72.1.7.7.10.696784.13260"
                        code of "4"
                        display of "HOV"
                    }
                type plus
                    codeableConcept {
                        text of "Office Visit"
                    }
                subject of reference(patientType, fakePatientId)
                identifier plus
                    identifier {
                        system of "mockEncounterCSNSystem"
                    }
            }
        val encounterId = MockEHRTestData.add(fakeEncounter)
        val aidboxEncounterId = "$tenantInUse-$encounterId"
        val aidboxEncounter =
            fakeEncounter.copy(
                id = Id(aidboxEncounterId),
                identifier = fakeEncounter.identifier + tenantIdentifier(testTenant) + fhirIdentifier(encounterId),
            )
        AidboxTestData.add(aidboxEncounter)

        val medicationRequest =
            medicationRequest {
                subject of reference(patientType, fakePatientId)
                encounter of reference(encounterType, encounterId)
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of
                    DynamicValues.codeableConcept(
                        codeableConcept {
                            text of "Example medication"
                        },
                    )
                identifier plus
                    identifier {
                        system of "mockEHROrderSystem"
                        value of "InternalMedReqId"
                    }
            }
        val medicationRequestId = MockEHRTestData.add(medicationRequest)
        val aidboxMedReqId = "$tenantInUse-$medicationRequestId"
        val aidboxMedReq =
            medicationRequest.copy(
                id = Id(aidboxMedReqId),
                identifier =
                    medicationRequest.identifier + tenantIdentifier(testTenant) +
                        fhirIdentifier(
                            medicationRequestId,
                        ),
            )

        val twoDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
        val medicationAdministration =
            medicationAdministration {
                subject of reference(patientType, fakePatientId)
                medication of
                    DynamicValues.codeableConcept(
                        codeableConcept {
                            text of "Example medication"
                        },
                    )
                effective of DynamicValues.dateTime(DateTime(twoDaysAgo.format(DateTimeFormatter.ISO_DATE_TIME)))
                dosage of
                    medAdminDosage {
                        dose of
                            quantity {
                                value of BigDecimal.TEN
                                unit of "mg"
                            }
                    }
                status of "completed"
                request of reference(medicationRequestType, medicationRequestId)
            }
        val medicationAdministrationId = MockEHRTestData.add(medicationAdministration)
        MockOCIServerClient.createExpectations(medicationAdministrationType, medicationAdministrationId, tenantInUse)
        // For Epic, we generate our own ID, so we need a format that will match that.
        MockOCIServerClient.createExpectations(medicationAdministrationType, "InternalMedReqId-.*", tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(aidboxMedReq),
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)

        val expectedAidboxResourceCount =
            if (testTenant == "cernmock") 0 else 1 // Cerner does not load for MedicationRequests
        assertEquals(expectedAidboxResourceCount, getAidboxResourceCount(medicationAdministrationType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakePatient = patient { }
        val patientId = MockEHRTestData.add(fakePatient)

        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val medicationAdministration =
            medicationAdministration {
                subject of reference(patientType, patientId)
                medication of DynamicValues.reference(reference(medicationType, "1234"))
                effective of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
            }
        val medicationAdministrationId = MockEHRTestData.add(medicationAdministration)
        MockOCIServerClient.createExpectations(medicationAdministrationType, medicationAdministrationId, tenantInUse)

        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(medicationAdministrationId),
            resourceType = ResourceType.MedicationAdministration,
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)

        val expectedAidboxResourceCount = if (testTenant == "epicmock") 0 else 1 // Epic cannot load for ad-hoc
        assertEquals(expectedAidboxResourceCount, getAidboxResourceCount(medicationAdministrationType))
    }

    @Test
    fun `channel works for MedicationAdministrations with contained Medications`() {
        // Only run for Cerner since it supports direct FHIR reads
        tenantInUse = "cernmock"

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
        val fakeAidboxPatientId = "$tenantInUse-$fakePatientId"
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

        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val medicationAdministration =
            medicationAdministration {
                subject of reference(patientType, fakePatientId)
                effective of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
                medication of DynamicValues.reference(Reference(reference = "#13579".asFHIR()))
                contained plus containedMedication
            }
        val medicationAdministrationId = MockEHRTestData.add(medicationAdministration)
        MockOCIServerClient.createExpectations(medicationAdministrationType, medicationAdministrationId, tenantInUse)

        val medicationId = "contained-$medicationAdministrationId-13579"
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
        assertEquals(1, getAidboxResourceCount(medicationAdministrationType))

        waitForMessage(2, channelID = medicationChannelId)
        val medicationMessageList = MirthClient.getChannelMessageIds(medicationChannelId)
        assertAllConnectorsStatus(medicationMessageList)
        assertEquals(2, medicationMessageList.size) // Also checks the Medication for child-references
        assertEquals(1, getAidboxResourceCount(medicationType))

        val storedMedicationAdministration =
            AidboxClient.getResource<MedicationAdministration>(
                medicationAdministrationType,
                "$tenantInUse-$medicationAdministrationId",
            )
        assertEquals(0, storedMedicationAdministration.contained.size)
        assertEquals(
            "Medication/$tenantInUse-$medicationId",
            (storedMedicationAdministration.medication?.value as? Reference)?.reference?.value,
        )

        val storedMedication =
            AidboxClient.getResource<Medication>(medicationType, "$tenantInUse-$medicationId")
        assertEquals(
            "insulin regular (human) IV additive 100 units [1 units/hr] + sodium chloride 0.9% drip 100 mL",
            storedMedication.code?.text?.value,
        )
    }

    @Test
    fun `channel works for MedicationAdministrations with codeable concept Medications`() {
        tenantInUse = "cernmock"

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
        val fakeAidboxPatientId = "$tenantInUse-$fakePatientId"
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
        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val medicationAdministration =
            medicationAdministration {
                subject of reference(patientType, fakePatientId)
                effective of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
                medication of DynamicValues.codeableConcept(medicationCodeableConcept)
            }
        val medicationAdministrationId = MockEHRTestData.add(medicationAdministration)
        MockOCIServerClient.createExpectations(medicationAdministrationType, medicationAdministrationId, tenantInUse)

        val medicationId = "codeable-$medicationAdministrationId-161"
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
        assertEquals(1, getAidboxResourceCount(medicationAdministrationType))

        waitForMessage(2, channelID = medicationChannelId)
        val medicationMessageList = MirthClient.getChannelMessageIds(medicationChannelId)
        assertAllConnectorsStatus(medicationMessageList)
        assertEquals(2, medicationMessageList.size) // Also checks the Medication for child-references
        assertEquals(1, getAidboxResourceCount(medicationType))

        val storedMedicationAdministration =
            AidboxClient.getResource<MedicationAdministration>(
                medicationAdministrationType,
                "$tenantInUse-$medicationAdministrationId",
            )
        assertEquals(0, storedMedicationAdministration.contained.size)
        assertEquals(
            "Medication/$tenantInUse-$medicationId",
            (storedMedicationAdministration.medication?.value as? Reference)?.reference?.value,
        )

        val storedMedication =
            AidboxClient.getResource<Medication>(medicationType, "$tenantInUse-$medicationId")
        assertEquals(medicationCodeableConcept.coding.first().code, storedMedication.code!!.coding.first().code)
    }
}
