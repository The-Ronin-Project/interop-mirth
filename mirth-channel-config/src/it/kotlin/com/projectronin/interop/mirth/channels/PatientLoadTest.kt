package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.time.ZoneOffset

const val patientLoadChannelName = "PatientLoad"

class PatientLoadTest : BaseChannelTest(
    patientLoadChannelName,
    listOf("Patient", "Condition", "Appointment", "Observation"),
    listOf("Patient", "Condition", "Appointment", "Observation")
) {
    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {
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
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patient1Id = MockEHRTestData.add(patient1)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)

        // push event to get picked up
        KafkaClient.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patient1Id),
            ResourceType.Patient
        )
        waitForMessage(1)

        val messages = getChannelMessageIds()
        assertAllConnectorsSent(messages)
        assertEquals(1, getAidboxResourceCount("Patient"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {
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
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patient1Id = MockEHRTestData.add(patient1)
        val patient2 = patient {
            birthDate of date {
                year of 1990
                month of 1
                day of 4
            }
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
                    value of "1000000002"
                }
            )
            name of listOf(
                name {
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "female"
            telecom of emptyList()
        }
        val patient2Id = MockEHRTestData.add(patient2)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)
        MockOCIServerClient.createExpectations("patient", patient2Id, testTenant)

        // push event to get picked up
        // push event to get picked up
        KafkaClient.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patient1Id, patient2Id),
            ResourceType.Patient
        )

        waitForMessage(1)
        val messages = getChannelMessageIds()
        assertAllConnectorsSent(messages)
        assertEquals(2, getAidboxResourceCount("Patient"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag`(testTenant: String) {
        val patientPublishTopics = KafkaClient.publishTopics(ResourceType.Patient)

        val conditionType = "Condition"
        val patientType = "Patient"
        val appointmentType = "Appointment"
        val observationType = "Observation"
        val types = listOf(
            patientType,
            conditionType,
            appointmentType
            // INT-1376 observationType
        )

        val channels = listOf(
            conditionLoadChannelName,
            appointmentLoadChannelName // INT-1376 observationLoadChannelName
        )
        val channelIds = channels.map {
            val id = installChannel(it)
            clearMessages(id)
            id
        }

        tenantInUse = testTenant
        val patient = patient {
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
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patientFhirId = MockEHRTestData.add(patient)
        MockOCIServerClient.createExpectations(patientType, patientFhirId, testTenant)

        val condition = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                        display of "Active"
                    }
                )
                text of "Active"
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of "http://terminology.hl7.org/CodeSystem/condition-category"
                            code of "problem-list-item"
                            display of "Problem list item"
                        }
                    )
                    text of "Problem List Item"
                }
            )
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://snomed.info/sct"
                        code of "1023001"
                        display of "Apnea"
                    }
                )
                text of "Apnea"
            }
            subject of reference(patientType, patientFhirId)
        }

        val conditionFhirId = MockEHRTestData.add(condition)
        MockOCIServerClient.createExpectations(conditionType, conditionFhirId, testTenant)
        val fakePractitioner = practitioner {
            identifier of listOf(
                identifier {
                    system of "mockEHRProviderSystem"
                }
            )
        }
        val fakeLocation = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                }
            )
        }
        val fakeLocationID = MockEHRTestData.add(fakeLocation)
        val fakePractitionerId = MockEHRTestData.add(fakePractitioner)
        val appointment = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patientFhirId)
                },
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", fakePractitionerId)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", fakeLocationID)
                }
            )
            minutesDuration of 8
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        val appointmentFhirId = MockEHRTestData.add(appointment)
        MockOCIServerClient.createExpectations(appointmentType, appointmentFhirId, tenantInUse)
        val observation = observation {
            subject of reference(patientType, patientFhirId)
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
        }
        val observationFhirId = MockEHRTestData.add(observation)

        MockOCIServerClient.createExpectations(observationType, observationFhirId)

        // deploy dag channels
        channelIds.forEach {
            deployAndStartChannel(channelToDeploy = it)
        }
        patientPublishTopics.forEach {
            KafkaClient.ensureStability(it.topicName)
        }
        // push event to get picked up
        val metadata = Metadata(runId = "patient1", runDateTime = OffsetDateTime.now(ZoneOffset.UTC))
        KafkaClient.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patientFhirId),
            ResourceType.Patient,
            metadata
        )
        waitForMessage(1)
        val patientPublishTopic =
            KafkaClient.publishTopics(ResourceType.Patient).first { it.topicName.contains("nightly") }
        KafkaClient.ensureStability(patientPublishTopic.topicName)
        channelIds.forEach {
            waitForMessage(1, channelID = it)
            stopChannel(it)
        }

        types.forEach {
            assertEquals(1, getAidboxResourceCount(it))
        }
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag when downstream processing is disabled`(testTenant: String) {
        val patientPublishTopics = KafkaClient.publishTopics(ResourceType.Patient)

        val conditionType = "Condition"
        val patientType = "Patient"
        val appointmentType = "Appointment"
        val otherTypes = listOf(
            conditionType,
            appointmentType
        )

        val channels = listOf(
            conditionLoadChannelName,
            appointmentLoadChannelName
        )
        val channelIds = channels.map {
            val id = installChannel(it)
            clearMessages(id)
            id
        }

        tenantInUse = testTenant
        val patient = patient {
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
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patientFhirId = MockEHRTestData.add(patient)
        MockOCIServerClient.createExpectations(patientType, patientFhirId, testTenant)

        val condition = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                        display of "Active"
                    }
                )
                text of "Active"
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of "http://terminology.hl7.org/CodeSystem/condition-category"
                            code of "problem-list-item"
                            display of "Problem list item"
                        }
                    )
                    text of "Problem List Item"
                }
            )
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://snomed.info/sct"
                        code of "1023001"
                        display of "Apnea"
                    }
                )
                text of "Apnea"
            }
            subject of reference(patientType, patientFhirId)
        }

        val conditionFhirId = MockEHRTestData.add(condition)
        MockOCIServerClient.createExpectations(conditionType, conditionFhirId, testTenant)
        val fakePractitioner = practitioner {
            identifier of listOf(
                identifier {
                    system of "mockEHRProviderSystem"
                }
            )
        }
        val fakeLocation = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                }
            )
        }
        val fakeLocationID = MockEHRTestData.add(fakeLocation)
        val fakePractitionerId = MockEHRTestData.add(fakePractitioner)
        val appointment = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patientFhirId)
                },
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", fakePractitionerId)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", fakeLocationID)
                }
            )
            minutesDuration of 8
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        val appointmentFhirId = MockEHRTestData.add(appointment)
        MockOCIServerClient.createExpectations(appointmentType, appointmentFhirId, tenantInUse)

        // deploy dag channels
        channelIds.forEach {
            deployAndStartChannel(channelToDeploy = it)
        }
        patientPublishTopics.forEach {
            KafkaClient.ensureStability(it.topicName)
        }
        // push event to get picked up
        val metadata = Metadata(runId = "patient1", runDateTime = OffsetDateTime.now(ZoneOffset.UTC))
        KafkaClient.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patientFhirId),
            ResourceType.Patient,
            metadata,
            InteropResourceLoadV1.FlowOptions(
                disableDownstreamResources = true
            )
        )
        waitForMessage(1)
        val patientPublishTopic =
            KafkaClient.publishTopics(ResourceType.Patient).first { it.topicName.contains("nightly") }
        KafkaClient.ensureStability(patientPublishTopic.topicName)

        // Even though we expect nothing, given 2 seconds before checking other channels haven't responded
        runBlocking { delay(2000) }

        channelIds.forEach {
            waitForMessage(0, channelID = it)
            stopChannel(it)
        }

        assertEquals(1, getAidboxResourceCount(patientType))
        otherTypes.forEach {
            assertEquals(0, getAidboxResourceCount(it))
        }
    }
}
