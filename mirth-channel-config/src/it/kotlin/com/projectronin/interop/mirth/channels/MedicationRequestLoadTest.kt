package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.period
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.medication
import com.projectronin.interop.fhir.generators.resources.medicationRequest
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.Dosage
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.Timing
import com.projectronin.interop.fhir.r4.datatype.TimingRepeat
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Medication
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
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
import com.projectronin.interop.mirth.channels.client.mirth.MEDICATION_REQUEST_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MedicationRequestLoadTest : BaseChannelTest(
    MEDICATION_REQUEST_LOAD_CHANNEL_NAME,
    listOf("Patient", "MedicationRequest", "Medication"),
    listOf("Patient", "MedicationRequest", "Medication"),
) {
    private val patientType = "Patient"
    private val medicationRequestType = "MedicationRequest"
    private val medicationType = "Medication"

    private val medicationChannelId = ChannelMap.installedDag[MEDICATION_LOAD_CHANNEL_NAME]!!

    val metadata =
        Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now(),
            targetedResources = listOf("Patient", "MedicationRequest", "Medication"),
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

        val twoDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
        val medicationRequest =
            medicationRequest {
                subject of reference(patientType, fakePatientId)
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of DynamicValues.reference(reference(medicationType, "1234"))
                dosageInstruction of
                    listOf(
                        Dosage(
                            timing =
                                Timing(
                                    repeat =
                                        TimingRepeat(
                                            bounds =
                                                DynamicValues.period(
                                                    period {
                                                        start of DateTime(twoDaysAgo.format(DateTimeFormatter.ISO_DATE_TIME))
                                                    },
                                                ),
                                        ),
                                ),
                        ),
                    )
            }
        val medicationRequestId = MockEHRTestData.add(medicationRequest)
        MockOCIServerClient.createExpectations(medicationRequestType, medicationRequestId, tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(medicationRequestType))
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

        val twoDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
        val fakeMedicationRequest1 =
            medicationRequest {
                subject of reference(patientType, patient1Id)
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of DynamicValues.reference(reference(medicationType, "1234"))
                dosageInstruction of
                    listOf(
                        Dosage(
                            timing =
                                Timing(
                                    repeat =
                                        TimingRepeat(
                                            bounds =
                                                DynamicValues.period(
                                                    period {
                                                        start of DateTime(twoDaysAgo.format(DateTimeFormatter.ISO_DATE_TIME))
                                                    },
                                                ),
                                        ),
                                ),
                        ),
                    )
            }

        val fakeMedicationRequest2 =
            medicationRequest {
                subject of reference(patientType, patient2Id)
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of DynamicValues.reference(reference(medicationType, "1234"))
                dosageInstruction of
                    listOf(
                        Dosage(
                            timing =
                                Timing(
                                    repeat =
                                        TimingRepeat(
                                            bounds =
                                                DynamicValues.period(
                                                    period {
                                                        start of DateTime(twoDaysAgo.format(DateTimeFormatter.ISO_DATE_TIME))
                                                    },
                                                ),
                                        ),
                                ),
                        ),
                    )
            }

        val medRequest1ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest2ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest3ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest4ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest5ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest6ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest7ID = MockEHRTestData.add(fakeMedicationRequest2)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest1ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest2ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest3ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest4ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest5ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest6ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest7ID, tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2),
            metadata = metadata,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount("MedicationRequest"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakePatient1 = patient {}
        val patient1Id = MockEHRTestData.add(fakePatient1)

        val fakeMedicationRequest1 =
            medicationRequest {
                subject of reference(patientType, patient1Id)
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of DynamicValues.reference(reference(medicationType, "1234"))
            }
        val fakeMedicationRequestId = MockEHRTestData.add(fakeMedicationRequest1)
        MockOCIServerClient.createExpectations("MedicationRequest", fakeMedicationRequestId, testTenant)
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeMedicationRequestId),
            resourceType = ResourceType.MedicationRequest,
            metadata = metadata,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("MedicationRequest"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = TEST_TENANT,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.MedicationRequest,
            metadata = metadata,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList, MirthResponseStatus.ERROR)
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount("MedicationRequest"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for MedicationRequests with contained Medications`(testTenant: String) {
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

        val containedMedication =
            medication {
                id of Id("13579")
                code of
                    codeableConcept {
                        text of "insulin regular (human) IV additive 100 units [1 units/hr] + sodium chloride 0.9% drip 100 mL"
                    }
            }

        val twoDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
        val medicationRequest =
            medicationRequest {
                subject of reference(patientType, fakePatientId)
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of DynamicValues.reference(Reference(reference = "#13579".asFHIR()))
                contained plus containedMedication
                dosageInstruction of
                    listOf(
                        Dosage(
                            timing =
                                Timing(
                                    repeat =
                                        TimingRepeat(
                                            bounds =
                                                DynamicValues.period(
                                                    period {
                                                        start of DateTime(twoDaysAgo.format(DateTimeFormatter.ISO_DATE_TIME))
                                                    },
                                                ),
                                        ),
                                ),
                        ),
                    )
            }
        val medicationRequestId = MockEHRTestData.add(medicationRequest)
        MockOCIServerClient.createExpectations(medicationRequestType, medicationRequestId, tenantInUse)

        val medicationId = "contained-$medicationRequestId-13579"
        MockOCIServerClient.createExpectations(medicationType, medicationId, tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(medicationRequestType))

        waitForMessage(2, channelID = medicationChannelId)
        val medicationMessageList = MirthClient.getChannelMessageIds(medicationChannelId)
        assertAllConnectorsStatus(medicationMessageList)
        assertEquals(2, medicationMessageList.size) // Also checks the Medication for child-references
        assertEquals(1, getAidboxResourceCount(medicationType))

        val storedMedicationRequest =
            AidboxClient.getResource<MedicationRequest>(medicationRequestType, "$tenantInUse-$medicationRequestId")
        assertEquals(0, storedMedicationRequest.contained.size)
        assertEquals(
            "Medication/$tenantInUse-$medicationId",
            (storedMedicationRequest.medication?.value as? Reference)?.reference?.value,
        )

        val storedMedication =
            AidboxClient.getResource<Medication>(medicationType, "$tenantInUse-$medicationId")
        assertEquals(
            "insulin regular (human) IV additive 100 units [1 units/hr] + sodium chloride 0.9% drip 100 mL",
            storedMedication.code?.text?.value,
        )
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for MedicationRequests with codeable concept Medications`(testTenant: String) {
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

        val twoDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)

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
        val medicationRequest =
            medicationRequest {
                subject of reference(patientType, fakePatientId)
                requester of reference("Practitioner", "ffff")
                intent of "order"
                status of "active"
                medication of DynamicValues.codeableConcept(medicationCodeableConcept)
                dosageInstruction of
                    listOf(
                        Dosage(
                            timing =
                                Timing(
                                    repeat =
                                        TimingRepeat(
                                            bounds =
                                                DynamicValues.period(
                                                    period {
                                                        start of DateTime(twoDaysAgo.format(DateTimeFormatter.ISO_DATE_TIME))
                                                    },
                                                ),
                                        ),
                                ),
                        ),
                    )
            }
        val medicationRequestId = MockEHRTestData.add(medicationRequest)
        MockOCIServerClient.createExpectations(medicationRequestType, medicationRequestId, tenantInUse)

        val medicationId = "codeable-$medicationRequestId-161"
        MockOCIServerClient.createExpectations(medicationType, medicationId, tenantInUse)
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient),
            metadata = metadata,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(medicationRequestType))

        waitForMessage(2, channelID = medicationChannelId)
        val medicationMessageList = MirthClient.getChannelMessageIds(medicationChannelId)
        assertAllConnectorsStatus(medicationMessageList)
        assertEquals(2, medicationMessageList.size) // Also checks the Medication for child-references
        assertEquals(1, getAidboxResourceCount(medicationType))

        val storedMedicationRequest =
            AidboxClient.getResource<MedicationRequest>(medicationRequestType, "$tenantInUse-$medicationRequestId")
        assertEquals(0, storedMedicationRequest.contained.size)
        assertEquals(
            "Medication/$tenantInUse-$medicationId",
            (storedMedicationRequest.medication?.value as? Reference)?.reference?.value,
        )

        val storedMedication =
            AidboxClient.getResource<Medication>(medicationType, "$tenantInUse-$medicationId")
        assertEquals(medicationCodeableConcept.coding.first().code, storedMedication.code!!.coding.first().code)
    }
}
