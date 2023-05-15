package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.externalIdentifier
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

const val appointmentByPractitionerLoadName = "AppointmentByPractitionerLoad"

class AppointmentByPractitionerLoadTest :
    BaseMirthChannelTest(
        appointmentByPractitionerLoadName,
        listOf("Practitioner", "Patient", "Appointment", "Condition", "Location")
    ) {
    val patientType = "Patient"
    val appointmentType = "Appointment"
    val conditionType = "Condition"
    val practitionerType = "Practitioner"
    val locationType = "Location"

    @Test
    fun `fails if no location`() {
        deployAndStartChannel(true)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, messageList.size)

        messageList.forEach { ids ->
            val message = MirthClient.getMessageById(testChannelId, ids)
            message.destinationMessages.forEach {
                assertTrue(it.status == "ERROR" || it.status == "FILTERED")
            }
        }
    }

    @Test
    fun `works - gets practitioner, appointment, condition, patient`() {
        val location = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "123"
                }
            )
        }
        val locationFhirId = MockEHRTestData.add(location)

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

        val appointment1 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference(patientType, patient1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
            minutesDuration of 60
        }
        val appointmentID = MockEHRTestData.add(appointment1)

        val condition1 = condition {
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
            subject of reference(patientType, patient1Id)
        }
        val conditionID = MockEHRTestData.add(condition1)

        val expectedMap = mapOf(
            patientType to listOf(patient1Id),
            conditionType to listOf(conditionID),
            appointmentType to listOf(appointmentID)
        )

        MockOCIServerClient.createExpectations(expectedMap)

        // Not particularly a fan of this method, but best I can come up with quickly
        val aidboxLocation1 = location.copy(
            identifier = location.identifier + tenantIdentifier(testTenant) + fhirIdentifier(locationFhirId)
        )
        AidboxTestData.add(aidboxLocation1)
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))
        assertEquals(0, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(locationType))
        assertEquals(0, getAidboxResourceCount(appointmentType))
        assertEquals(0, getAidboxResourceCount(conditionType))

        deployAndStartChannel(true)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, messageList.size)

        assertAllConnectorsSent(messageList)
        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(appointmentType))
        assertEquals(1, getAidboxResourceCount(conditionType))

        // ensure data lake gets what it needs
        // MockOCIServerClient.verify(3)
        // val resources = MockOCIServerClient.getAllPublishPutsAsResources()
        // verifyAllPresent(resources, expectedMap)
    }

    @Test
    fun `works - gets only active and allowed condition categories`() {
        val practitioner1 = practitioner {
            identifier of listOf(
                identifier {},
                externalIdentifier {
                    system of "mockEHRProviderSystem"
                    value of "1234"
                }
            )
        }
        val practitioner1Id = MockEHRTestData.add(practitioner1)
        val location = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "123"
                }
            )
        }
        val locationFhirId = MockEHRTestData.add(location)
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
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patient1Id = MockEHRTestData.add(patient1)

        val appointment1 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference(practitionerType, practitioner1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference(patientType, patient1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
            minutesDuration of 60
        }
        val appointmentID = MockEHRTestData.add(appointment1)

        val condition1 = condition {
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
            subject of reference(patientType, patient1Id)
        }
        val condition2 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                        display of "Active"
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
            subject of reference(patientType, patient1Id)
        }
        val condition3 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                        display of "Active"
                    }
                )
            }
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of "http://hl7.org/fhir/us/core/CodeSystem/condition-category"
                            code of ConditionCategoryCodes.HEALTH_CONCERN.code
                            display of "Health concern"
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
            subject of reference(patientType, patient1Id)
        }
        val condition4 = condition {
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "remission"
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
            subject of reference(patientType, patient1Id)
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
            subject of reference(patientType, patient1Id)
        }

        val cond1Id = MockEHRTestData.add(condition1)
        val cond2Id = MockEHRTestData.add(condition2)
        val cond3Id = MockEHRTestData.add(condition3)
        MockEHRTestData.add(condition4)
        MockEHRTestData.add(condition5)

        val expectedMap = mapOf(
            patientType to listOf(patient1Id),
            conditionType to listOf(cond1Id, cond2Id, cond3Id),
            appointmentType to listOf(appointmentID)
        )
        MockOCIServerClient.createExpectations(expectedMap)

        val aidboxLocation1 = location.copy(
            identifier = location.identifier + tenantIdentifier(testTenant) + fhirIdentifier(locationFhirId)
        )
        AidboxTestData.add(aidboxLocation1)
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))

        assertEquals(0, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(locationType))
        assertEquals(0, getAidboxResourceCount(appointmentType))
        assertEquals(0, getAidboxResourceCount(conditionType))

        deployAndStartChannel(true)
        // this one is moving slow for w/e reason
        pause()

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, messageList.size)

        assertAllConnectorsSent(messageList)

        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(appointmentType))
        assertEquals(3, getAidboxResourceCount(conditionType))

        // ensure data lake gets what it needs
        // MockOCIServerClient.verify(5)
        // val resources = MockOCIServerClient.getAllPublishPutsAsResources()
        // verifyAllPresent(resources, expectedMap)
    }
}
