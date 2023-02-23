package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.codeableConcept
import com.projectronin.interop.mirth.channels.client.data.datatypes.coding
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.datatypes.participant
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.primitives.date
import com.projectronin.interop.mirth.channels.client.data.primitives.daysFromNow
import com.projectronin.interop.mirth.channels.client.data.resources.appointment
import com.projectronin.interop.mirth.channels.client.data.resources.condition
import com.projectronin.interop.mirth.channels.client.data.resources.observation
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

const val patientLoadChannelName = "PatientLoad"

class PatientLoadTest : BaseChannelTest(
    patientLoadChannelName,
    listOf("Patient", "Condition", "Appointment", "Observation"),
    listOf("Patient", "Condition", "Appointment", "Observation"),
    listOf(ResourceType.PATIENT, ResourceType.APPOINTMENT, ResourceType.CONDITION)
) {
    override val groupId = "interop-mirth-patient"

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
                }
            )
            gender of "male"
        }
        val patient1Id = MockEHRTestData.add(patient1)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)
        // push event to get picked up
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patient1Id),
            ResourceType.PATIENT
        )
        deployAndStartChannel(true)

        // check that publish event was triggered
        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY, groupId)
        assertEquals(1, events.size)
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
                }
            )
            gender of "male"
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
                }
            )
            gender of "female"
        }
        val patient2Id = MockEHRTestData.add(patient2)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)
        MockOCIServerClient.createExpectations("patient", patient2Id, testTenant)

        // push event to get picked up
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patient1Id, patient2Id),
            ResourceType.PATIENT
        )
        deployAndStartChannel(true)

        // check that publish event was triggered
        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY, groupId)
        assertEquals(2, events.size)
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag`(testTenant: String) {
        drainKafkaEvents(ResourceType.PATIENT, overrideGroupId = "interop-mirth-condition")
        drainKafkaEvents(ResourceType.PATIENT, overrideGroupId = "interop-mirth-appointment")
        drainKafkaEvents(ResourceType.PATIENT, overrideGroupId = "interop-mirth-patient")

        val conditionType = "Condition"
        val patientType = "Patient"
        val appointmentType = "Appointment"
        val observationType = "Observation"
        val types = listOf(
            patientType,
            conditionType,
            appointmentType,
            // INT-1376 observationType
        )

        val channels = listOf(
            conditionLoadChannelName, appointmentLoadChannelName, // INT-1376 observationLoadChannelName
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
                }
            )
            gender of "male"
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

        val appointment = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patientFhirId)
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

        // push event to get picked up
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patientFhirId),
            ResourceType.PATIENT
        )
        deployAndStartChannel(waitForMessage = true)
        channelIds.forEach {
            deployAndStartChannel(
                waitForMessage = true,
                channelToDeploy = it
            )
            stopChannel(it)
        }

        // check that publish event was triggered
        val conditionEvents = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.CONDITION, DataTrigger.NIGHTLY, "interop-mirth-patient")
        val appointmentEvents = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.APPOINTMENT, DataTrigger.NIGHTLY, "interop-mirth-patient")
        // INT-1376 val observationEvents = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.OBSERVATION, DataTrigger.NIGHTLY)
        assertEquals(1, conditionEvents.size)
        assertEquals(1, appointmentEvents.size)
        // INT-1376 assertEquals(1, observationEvents.size)
        types.forEach {
            assertEquals(1, getAidboxResourceCount(it))
        }
    }
}
