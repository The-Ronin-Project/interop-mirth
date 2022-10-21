package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.codeableConcept
import com.projectronin.interop.mirth.channels.client.data.datatypes.coding
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.resources.condition
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val conditionQueueChannelName = "ConditionQueue"

class ConditionQueueTest : BaseMirthChannelTest(conditionQueueChannelName, listOf("Condition")) {
    private val conditionType = "Condition"
    private val patientType = "Patient"

    @Test
    fun `conditions can be queued`() {
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
        MockEHRTestData.add(condition)
        assertEquals(0, getAidboxResourceCount(conditionType))

        // query for conditions from 'EHR'
        ProxyClient.getConditionsByPatient(testTenant, patientId)

        // start channel
        deployAndStartChannel(true)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        assertAllConnectorsSent(list)
        // condition successfully added to Aidbox
        assertEquals(1, getAidboxResourceCount(conditionType))
    }

    @Test
    fun `no data no message`() {
        assertEquals(0, getAidboxResourceCount(conditionType))
        // start channel
        deployAndStartChannel(false)
        // just wait a moment
        runBlocking {
            delay(1000)
        }
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(0, list.size)
        // nothing added
        assertEquals(0, getAidboxResourceCount(conditionType))
    }
}
