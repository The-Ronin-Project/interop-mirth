package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

const val conditionLoadChannelName = "ConditionLoad"

class ConditionLoadTest : BaseChannelTest(
    conditionLoadChannelName,
    listOf("Patient", "Condition"),
    listOf("Patient", "Condition")
) {
    override val groupId = "interop-mirth-condition"

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
        val aidboxPatientId = "$tenantInUse-$patient1Id"
        val aidboxPatient = patient1.copy(
            id = Id(aidboxPatientId),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
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
        MockOCIServerClient.createExpectations("Condition", conditionID, tenantInUse)
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(aidboxPatient)
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Condition"))

        Assertions.assertTrue(
            KafkaWrapper.validatePublishEvents(
                1,
                ResourceType.CONDITION,
                DataTrigger.NIGHTLY,
                groupId
            )
        )
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and conditions`(testTenant: String) {
        tenantInUse = testTenant

        // mock: patients at the EHR got published to Ronin

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

        val aidboxPatient1Id = "$tenantInUse-$patient1Id"
        val aidboxPatient2Id = "$tenantInUse-$patient2Id"
        val aidboxPatient1 = patient1.copy(
            id = Id(aidboxPatient1Id),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )
        val aidboxPatient2 = patient2.copy(
            id = Id(aidboxPatient2Id),
            identifier = patient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient2Id)
        )
        AidboxTestData.add(aidboxPatient1)
        AidboxTestData.add(aidboxPatient2)

        // mock: conditions at the EHR
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
                            system of "http://hl7.org/fhir/us/core/CodeSystem/condition-category"
                            code of "health-concern"
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
            subject of reference("Patient", patient2Id)
        }
        val condition1ID = MockEHRTestData.add(condition1)
        val condition2ID = MockEHRTestData.add(condition1)
        val condition3ID = MockEHRTestData.add(condition1)
        val condition4ID = MockEHRTestData.add(condition1)
        val condition5ID = MockEHRTestData.add(condition1)
        val condition6ID = MockEHRTestData.add(condition1)
        val conditionPat2ID = MockEHRTestData.add(condition2)
        MockOCIServerClient.createExpectations("Condition", condition1ID, tenantInUse)
        MockOCIServerClient.createExpectations("Condition", condition2ID, tenantInUse)
        MockOCIServerClient.createExpectations("Condition", condition3ID, tenantInUse)
        MockOCIServerClient.createExpectations("Condition", condition4ID, tenantInUse)
        MockOCIServerClient.createExpectations("Condition", condition5ID, tenantInUse)
        MockOCIServerClient.createExpectations("Condition", condition6ID, tenantInUse)
        MockOCIServerClient.createExpectations("Condition", conditionPat2ID, tenantInUse)

        // mock: patient-publish event
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(aidboxPatient1, aidboxPatient2)
        )

        // larger data sets: make sure MockEHR is OK
        MockEHRTestData.validateAll()

        // start channel: patient-publish triggers condition-load
        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount("Condition"))

        // verify: condition-publish success
        Assertions.assertTrue(
            KafkaWrapper.validatePublishEvents(
                7,
                ResourceType.CONDITION,
                DataTrigger.AD_HOC,
                groupId
            )
        )
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
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
        MockOCIServerClient.createExpectations("Condition", conditionID, testTenant)
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

        Assertions.assertTrue(
            KafkaWrapper.validatePublishEvents(
                1,
                ResourceType.CONDITION,
                DataTrigger.AD_HOC,
                groupId
            )
        )
    }

    @Test
    fun `non-existent request errors`() {
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
    }
}
