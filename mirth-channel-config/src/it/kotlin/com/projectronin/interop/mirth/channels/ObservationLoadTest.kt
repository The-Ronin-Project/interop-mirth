package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.conditionStage
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

const val observationLoadChannelName = "ObservationLoad"

class ObservationLoadTest : BaseChannelTest(
    observationLoadChannelName,
    listOf("Patient", "Observation", "Condition"),
    listOf("Patient", "Observation", "Condition")
) {
    val patientType = "Patient"
    val observationType = "Observation"
    private val nowDate =
        DynamicValues.dateTime(DateTime(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works triggered by patient publish`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {}
        val patient1Id = MockEHRTestData.add(patient1)
        val roninPatient = patient1.copy(
            id = Id("$tenantInUse-$patient1Id"),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )

        val observation = observation {
            subject of reference(patientType, patient1Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            effective of nowDate
            encounter of Reference(type = Uri("Encounter"), display = "display".asFHIR())
        }
        val obsvId = MockEHRTestData.add(observation)

        MockOCIServerClient.createExpectations(observationType, obsvId)

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninPatient)
        )
        waitForMessage(1)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(observationType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `repeat patients are ignored`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {}
        val patient1Id = MockEHRTestData.add(patient1)
        val roninPatient = patient1.copy(
            id = Id("$tenantInUse-$patient1Id"),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )

        val observation = observation {
            subject of reference(patientType, patient1Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            effective of nowDate
            encounter of Reference(type = Uri("Encounter"), display = "display".asFHIR())
        }
        val obsvId = MockEHRTestData.add(observation)

        MockOCIServerClient.createExpectations(observationType, obsvId)

        val metadata = Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now()
        )
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninPatient),
            metadata = metadata
        )
        waitForMessage(1)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(observationType))

        // Now publish the same event
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninPatient),
            metadata = metadata
        )

        waitForMessage(2)
        val messageList2 = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList2)
        assertEquals(2, messageList2.size)
        assertEquals(1, getAidboxResourceCount(observationType))

        // The message IDs are actually in reverse order, so grabbing the first
        val message = MirthClient.getMessageById(testChannelId, messageList2.first())
        val publishResponse =
            message.destinationMessages.find { it.connectorName == "Publish Observations" }!!.response!!
        assertTrue(publishResponse.content.contains("<message>All requested resources have already been processed this run: 123456:Patient:$tenantInUse:$patient1Id</message>"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works triggered by condition publish`(testTenant: String) {
        tenantInUse = testTenant

        val observation = observation {
            subject of reference(patientType, "123")
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            encounter of Reference(type = Uri("Encounter"), display = "display".asFHIR())
        }
        val obsvId = MockEHRTestData.add(observation)
        MockOCIServerClient.createExpectations(observationType, obsvId)

        val condition1 = condition {
            stage of listOf(
                conditionStage {
                    assessment of listOf(
                        reference("Observation", obsvId)
                    )
                }
            )
        }
        val condition1Id = MockEHRTestData.add(condition1)

        val roninCondition = condition1.copy(
            id = Id("$tenantInUse-$condition1Id"),
            stage = condition1.stage.map { stage ->
                stage.copy(
                    assessment = stage.assessment.map { reference ->
                        reference.copy(
                            id = FHIRString("$tenantInUse-$obsvId")
                        )
                    }
                )
            }
        )

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninCondition)
        )
        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(observationType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `repeat conditions are ignored`(testTenant: String) {
        tenantInUse = testTenant

        val observation = observation {
            subject of reference(patientType, "123")
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            encounter of Reference(type = Uri("Encounter"), display = "display".asFHIR())
        }
        val obsvId = MockEHRTestData.add(observation)
        MockOCIServerClient.createExpectations(observationType, obsvId)

        val condition1 = condition {
            stage of listOf(
                conditionStage {
                    assessment of listOf(
                        reference("Observation", obsvId)
                    )
                }
            )
        }
        val condition1Id = MockEHRTestData.add(condition1)

        val roninCondition = condition1.copy(
            id = Id("$tenantInUse-$condition1Id"),
            stage = condition1.stage.map { stage ->
                stage.copy(
                    assessment = stage.assessment.map { reference ->
                        reference.copy(
                            id = FHIRString("$tenantInUse-$obsvId"),
                            reference = FHIRString("Observation/$tenantInUse-$obsvId")
                        )
                    }
                )
            }
        )

        val metadata = Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now()
        )
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninCondition),
            metadata = metadata
        )
        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(observationType))

        // Now publish the same event.
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninCondition),
            metadata = metadata
        )

        waitForMessage(2)
        val messageList2 = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList2)
        assertEquals(2, messageList2.size)
        assertEquals(1, getAidboxResourceCount(observationType))

        // The message IDs are actually in reverse order, so grabbing the first
        val message = MirthClient.getMessageById(testChannelId, messageList2.first())
        val publishResponse =
            message.destinationMessages.find { it.connectorName == "Publish Observations" }!!.response!!
        assertTrue(publishResponse.content.contains("<message>All requested resources have already been processed this run: 123456:Observation:$testTenant:$obsvId</message>"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients, conditions and observations`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {}
        val patient1Id = MockEHRTestData.add(patient1)

        val patient2 = patient {}
        val patient2Id = MockEHRTestData.add(patient2)

        val roninPatient1 = patient1.copy(
            id = Id("$tenantInUse-$patient1Id"),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )
        val roninPatient2 = patient2.copy(
            id = Id("$tenantInUse-$patient2Id"),
            identifier = patient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient2Id)
        )

        val observation1 = observation {
            subject of reference(patientType, patient1Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            effective of nowDate
            encounter of Reference(type = Uri("Encounter"), display = "display".asFHIR())
        }

        val observation2 = observation {
            subject of reference(patientType, patient2Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            effective of nowDate
            encounter of Reference(type = Uri("Encounter"), display = "display".asFHIR())
        }
        val observation1ID = MockEHRTestData.add(observation1)
        val observation2ID = MockEHRTestData.add(observation1)
        val observation3ID = MockEHRTestData.add(observation1)
        val observation4ID = MockEHRTestData.add(observation1)
        val observation5ID = MockEHRTestData.add(observation1)
        val observationConditionID = MockEHRTestData.add(observation1)
        val observationPat2ID = MockEHRTestData.add(observation2)
        MockOCIServerClient.createExpectations(observationType, observation1ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation2ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation3ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation4ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation5ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observationConditionID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observationPat2ID, tenantInUse)

        val condition = condition {
            stage of listOf(
                conditionStage {
                    assessment of listOf(
                        reference("Observation", observationConditionID)
                    )
                }
            )
        }
        val conditionId = MockEHRTestData.add(condition)

        val roninCondition = condition.copy(
            id = Id("$tenantInUse-$conditionId"),
            stage = condition.stage.map { stage ->
                stage.copy(
                    assessment = stage.assessment.map { reference ->
                        reference.copy(
                            id = FHIRString("$tenantInUse-$observationConditionID")
                        )
                    }
                )
            }
        )
        // make sure MockEHR is OK
        MockEHRTestData.validateAll()

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2, roninCondition)
        )

        // 2 because we group the Patients and Conditions into individual messages
        waitForMessage(2, 30)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount(observationType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {}
        val patient1Id = MockEHRTestData.add(patient1)
        val observation = observation {
            subject of reference(patientType, patient1Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            encounter of Reference(type = Uri("Encounter"), display = "display".asFHIR())
        }
        val observationID = MockEHRTestData.add(observation)
        MockOCIServerClient.createExpectations(observationType, observationID, testTenant)
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(observationID),
            resourceType = ResourceType.Observation
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(observationType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `non-existent request errors`() {
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.Observation
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        messageList.forEach { ids ->
            val message = MirthClient.getMessageById(testChannelId, ids)
            message.destinationMessages.forEach {
                assertEquals("ERROR", it.status)
            }
        }
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount(observationType))
    }
}
