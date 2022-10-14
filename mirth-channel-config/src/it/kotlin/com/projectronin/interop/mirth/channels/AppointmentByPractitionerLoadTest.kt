package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MirthClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.appointment
import com.projectronin.interop.mirth.channels.client.codeableConcept
import com.projectronin.interop.mirth.channels.client.coding
import com.projectronin.interop.mirth.channels.client.condition
import com.projectronin.interop.mirth.channels.client.daysFromNow
import com.projectronin.interop.mirth.channels.client.externalIdentifier
import com.projectronin.interop.mirth.channels.client.identifier
import com.projectronin.interop.mirth.channels.client.name
import com.projectronin.interop.mirth.channels.client.participant
import com.projectronin.interop.mirth.channels.client.patient
import com.projectronin.interop.mirth.channels.client.practitioner
import com.projectronin.interop.mirth.channels.client.reference
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

const val appointmentByPractitionerLoadName = "AppointmentByPractitionerLoad"

class AppointmentByPractitionerLoadTest :
    BaseMirthChannelTest(
        appointmentByPractitionerLoadName,
        listOf("Practitioner", "Patient", "Appointment", "Condition")
    ) {
    private final val locationFhirId = "3f1af7cb-a47e-4e1e-a8e3-d18e0d073e6c"

    @Test
    fun `fails if no practitioner`() {
        deployAndStartChannel(true)

        val jsonNode = MirthClient.getChannelMessages(testChannelId)

        val list = jsonNode.get("list")
        assertEquals(1, list.size())

        val connectorMessageByConnector = getConnectorMessageByConnector(list)
        assertEquals(2, connectorMessageByConnector.size)

        val expectedErrorMessage = "No Practitioners found in clinical data store for tenant $testTenant"

        val conditionMessage = connectorMessageByConnector["Conditions"]!!
        val conditionContent = conditionMessage.get("raw").get("content").asText()
        println(conditionContent)
        assertTrue(conditionContent.contains(expectedErrorMessage))

        val appointmentMessage = connectorMessageByConnector["Appointments"]!!
        val appointmentContent = appointmentMessage.get("raw").get("content").asText()
        assertTrue(appointmentContent.contains(expectedErrorMessage))
    }

    @Test
    fun `works - gets practitioner, appointment, condition, patient`() {
        val practitioner1 = practitioner {
            identifier generate 1 plus externalIdentifier {
                system of "mockEHRProviderSystem"
                value of "1234"
            }
        }
        val practitioner1Id = MockEHRTestData.add(practitioner1)

        val patient1 = patient {
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
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

        val appointment1 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", practitioner1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Patient", patient1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        MockEHRTestData.add(appointment1)

        val condition1 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                    }
                )
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
            subject of reference("Patient", patient1Id)
        }
        MockEHRTestData.add(condition1)

        // Not particularly a fan of this method, but best I can come up with quickly
        val aidboxPractitioner1 = practitioner1.copy(
            identifier = practitioner1.identifier + tenantIdentifier(testTenant)
        )
        AidboxTestData.add(aidboxPractitioner1)

        assertEquals(0, getAidboxResourceCount("Patient"))
        assertEquals(1, getAidboxResourceCount("Practitioner"))
        assertEquals(0, getAidboxResourceCount("Appointment"))
        assertEquals(0, getAidboxResourceCount("Condition"))

        deployAndStartChannel(true)

        val jsonNode = MirthClient.getChannelMessages(testChannelId)
        val list = jsonNode.get("list")
        assertEquals(1, list.size())
        assertAllConnectorsSent(list)

        assertEquals(1, getAidboxResourceCount("Patient"))
        assertEquals(1, getAidboxResourceCount("Appointment"))
        assertEquals(1, getAidboxResourceCount("Condition"))
    }

    @Test
    fun `works - gets only active and allowed condition categories`() {
        val practitioner1 = practitioner {
            identifier generate 1 plus externalIdentifier {
                system of "mockEHRProviderSystem"
                value of "1234"
            }
        }
        val practitioner1Id = MockEHRTestData.add(practitioner1)

        val patient1 = patient {
            identifier of listOf(
                identifier {
                    system of "mockPatientInternalSystem"
                },
                identifier {
                    system of "mockEHRMRNSystem"
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

        val appointment1 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", practitioner1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Patient", patient1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        MockEHRTestData.add(appointment1)

        val condition1 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                    }
                )
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of "http://terminology.hl7.org/CodeSystem/condition-category"
                            code of ConditionCategoryCodes.PROBLEM_LIST_ITEM.code
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
            subject of reference("Patient", patient1Id)
        }
        val condition2 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                    }
                )
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of "http://terminology.hl7.org/CodeSystem/condition-category"
                            code of ConditionCategoryCodes.ENCOUNTER_DIAGNOSIS.code
                            display of "Encounter Diagnosis"
                        }
                    )
                    text of "Encounter Diagnosis"
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
            subject of reference("Patient", patient1Id)
        }
        val condition3 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                    }
                )
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of "http://hl7.org/fhir/us/core/ValueSet/us-core-condition-category"
                            code of "health-concern"
                            display of "Health Concern"
                        }
                    )
                    text of "Health Concern"
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
            subject of reference("Patient", patient1Id)
        }
        val condition4 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "inactive"
                    }
                )
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of "http://terminology.hl7.org/CodeSystem/condition-category"
                            code of ConditionCategoryCodes.PROBLEM_LIST_ITEM.code
                            display of "Problem List Item"
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
            subject of reference("Patient", patient1Id)
        }
        val condition5 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "inactive"
                    }
                )
            }
            category of emptyList()
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
            subject of reference("Patient", patient1Id)
        }

        MockEHRTestData.add(condition1)
        MockEHRTestData.add(condition2)
        MockEHRTestData.add(condition3)
        MockEHRTestData.add(condition4)
        MockEHRTestData.add(condition5)

        // Not particularly a fan of this method, but best I can come up with quickly
        val aidboxPractitioner1 = practitioner1.copy(
            identifier = practitioner1.identifier + tenantIdentifier(testTenant)
        )
        AidboxTestData.add(aidboxPractitioner1)

        assertEquals(0, getAidboxResourceCount("Patient"))
        assertEquals(1, getAidboxResourceCount("Practitioner"))
        assertEquals(0, getAidboxResourceCount("Appointment"))
        assertEquals(0, getAidboxResourceCount("Condition"))

        deployAndStartChannel(true)

        val jsonNode = MirthClient.getChannelMessages(testChannelId)
        val list = jsonNode.get("list")
        assertEquals(1, list.size())
        assertAllConnectorsSent(list)

        assertEquals(1, getAidboxResourceCount("Patient"))
        assertEquals(1, getAidboxResourceCount("Appointment"))
        assertEquals(2, getAidboxResourceCount("Condition"))
    }
}
