package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.codeableConcept
import com.projectronin.interop.mirth.channels.client.data.datatypes.coding
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.resources.condition
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

const val kafkaConditionQueueChannelName = "KafkaConditionQueue"

class KafkaConditionQueueTest : BaseChannelTest(kafkaConditionQueueChannelName, listOf("Condition")) {
    private val conditionType = "Condition"
    private val patientType = "Patient"

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `conditions can be queued`(testTenant: String) {
        tenantInUse = testTenant
        val patientId = MockEHRTestData.add(patient {})
        // all of these values are requires
        val condition = condition {
            subject of reference(patientType, patientId)
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
            clinicalStatus of codeableConcept {
                coding of listOf(
                    coding {
                        system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                        code of "active"
                    }
                )
            }
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
        }
        val conditionFhirId = MockEHRTestData.add(condition)
        MockOCIServerClient.createExpectations(conditionType, conditionFhirId, testTenant)
        assertEquals(0, getAidboxResourceCount(conditionType))

        // query for conditions from 'EHR'
        ProxyClient.getConditionsByPatient(testTenant, patientId)

        // start channel
        deployAndStartChannel(true)
        // make sure a message queued in mirth
        waitForMessage(1)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        assertAllConnectorsSent(list)

        // condition successfully added to Aidbox
        assertEquals(1, getAidboxResourceCount(conditionType))

        // datalake received the object
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Condition::class)
        assertEquals(conditionFhirId, datalakeFhirResource.getFhirIdentifier()?.value?.value)
    }
}
