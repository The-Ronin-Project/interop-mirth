package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.codeableConcept
import com.projectronin.interop.mirth.channels.client.data.datatypes.coding
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.primitives.date
import com.projectronin.interop.mirth.channels.client.data.resources.condition
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val conditionLoadChannelName = "ConditionLoad"

class ConditionLoadTest : BaseMirthChannelTest(
    conditionLoadChannelName,
    listOf("Patient", "Condition"),
    listOf("Patient", "Condition"),
    listOf(ResourceType.PATIENT, ResourceType.CONDITION)
) {
    @Test
    fun `channel works`() {
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
        val aidboxPatientId = "$testTenant-$patient1Id"
        val aidboxPatient = patient1.copy(
            id = Id(aidboxPatientId),
            identifier = patient1.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patient1Id)
        )
        AidboxTestData.add(aidboxPatient)

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
            subject of reference("Patient", patient1Id)
        }
        val conditionID = MockEHRTestData.add(condition1)
        MockOCIServerClient.createExpectations("Condition", conditionID)
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = testTenant,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(aidboxPatient),
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Condition"))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.CONDITION, DataTrigger.NIGHTLY)
        assertEquals(1, events.size)
    }

    @Test
    fun `channel works with multiple patients and conditions`() {
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
                day of 3
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
            gender of "male"
        }
        val patient2Id = MockEHRTestData.add(patient2)

        val aidboxPatient1Id = "$testTenant-$patient1Id"
        val aidboxPatient2Id = "$testTenant-$patient2Id"
        val aidboxPatient1 = patient1.copy(
            id = Id(aidboxPatient1Id),
            identifier = patient1.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patient1Id)
        )
        val aidboxPatient2 = patient2.copy(
            id = Id(aidboxPatient2Id),
            identifier = patient2.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patient2Id)
        )
        AidboxTestData.add(aidboxPatient1)
        AidboxTestData.add(aidboxPatient2)

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
            subject of reference("Patient", patient1Id)
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
            subject of reference("Patient", patient2Id)
        }
        val condition1ID = MockEHRTestData.add(condition1)
        val condition2ID = MockEHRTestData.add(condition1)
        val condition3ID = MockEHRTestData.add(condition1)
        val condition4ID = MockEHRTestData.add(condition1)
        val condition5ID = MockEHRTestData.add(condition1)
        val condition6ID = MockEHRTestData.add(condition1)
        val conditionPat2ID = MockEHRTestData.add(condition2)
        MockOCIServerClient.createExpectations("Condition", condition1ID)
        MockOCIServerClient.createExpectations("Condition", condition2ID)
        MockOCIServerClient.createExpectations("Condition", condition3ID)
        MockOCIServerClient.createExpectations("Condition", condition4ID)
        MockOCIServerClient.createExpectations("Condition", condition5ID)
        MockOCIServerClient.createExpectations("Condition", condition6ID)
        MockOCIServerClient.createExpectations("Condition", conditionPat2ID)
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(aidboxPatient1, aidboxPatient2),
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount("Condition"))

        assertAllConnectorsSent(messageList)

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.CONDITION, DataTrigger.AD_HOC)
        assertEquals(7, events.size)
    }

    @Test
    fun `channel works for ad-hoc requests`() {
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
        val aidboxPatientId = "$testTenant-$patient1Id"
        val aidboxPatient = patient1.copy(
            id = Id(aidboxPatientId),
            identifier = patient1.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patient1Id)
        )
        AidboxTestData.add(aidboxPatient)

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
            subject of reference("Patient", patient1Id)
        }
        val conditionID = MockEHRTestData.add(condition1)
        MockOCIServerClient.createExpectations("Condition", conditionID)
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(conditionID),
            resourceType = ResourceType.CONDITION
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Condition"))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.CONDITION, DataTrigger.AD_HOC)
        assertEquals(1, events.size)
    }

    @Test
    fun `non-existant request errors`() {
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.CONDITION
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        messageList.forEach { ids ->
            val message = MirthClient.getMessageById(testChannelId, ids)
            message.destinationMessages.forEach {
                assertEquals("ERROR", it.status)
            }
        }
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount("Condition"))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.CONDITION, DataTrigger.AD_HOC)
        assertEquals(0, events.size)
    }
}