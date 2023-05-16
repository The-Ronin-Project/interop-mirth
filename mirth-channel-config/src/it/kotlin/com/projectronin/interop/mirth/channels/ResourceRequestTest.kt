package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.condition
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

const val resourceRequestChannelName = "ResourceRequest"

class ResourceRequestTest : BaseChannelTest(
    resourceRequestChannelName,
    listOf("Patient", "Condition"),
    listOf("Patient", "Condition")
) {
    private val resourceRequestTopic = KafkaClient.requestSpringConfig.requestTopic()
    private val patientLoadTopic = KafkaClient.loadTopic(ResourceType.Patient)
    private val conditionLoadTopic = KafkaClient.loadTopic(ResourceType.Condition)
    private val patientPublishTopics = KafkaClient.publishTopics(ResourceType.Patient)

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag`(testTenant: String) {
        val conditionType = "Condition"
        val patientType = "Patient"

        val channels = listOf(
            patientLoadChannelName,
            conditionLoadChannelName
        )
        val channelNamesToIds = channels.associateWith {
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
            subject of reference(patientType, "completelyDifferentPatient")
        }

        val conditionFhirId = MockEHRTestData.add(condition)
        MockOCIServerClient.createExpectations(conditionType, conditionFhirId, testTenant)

        deployAndStartChannel()
        // other dag channels
        channelNamesToIds.values.forEach {
            deployAndStartChannel(channelToDeploy = it)
        }
        KafkaClient.ensureStability(resourceRequestTopic.topicName)
        // patient channel
        KafkaClient.ensureStability(patientLoadTopic.topicName)

        // condition channel
        patientPublishTopics.forEach {
            KafkaClient.ensureStability(it.topicName)
        }
        KafkaClient.ensureStability(conditionLoadTopic.topicName)

        // push event to get picked up
        KafkaClient.kafkaRequestService.pushRequestEvent(
            testTenant,
            listOf(patientFhirId),
            ResourceType.Patient,
            "testing"
        )
        KafkaClient.kafkaRequestService.pushRequestEvent(
            testTenant,
            listOf(conditionFhirId),
            ResourceType.Condition,
            "testing"
        )
        waitForMessage(2)
        waitForMessage(1, channelID = channelNamesToIds[patientLoadChannelName]!!)
        waitForMessage(2, channelID = channelNamesToIds[conditionLoadChannelName]!!)
        channelNamesToIds.values.forEach {
            stopChannel(it)
        }

        // patient should have just one event, but
        val patientMessages = MirthClient.getChannelMessageIds(channelNamesToIds[patientLoadChannelName]!!)
        val conditionMessages = MirthClient.getChannelMessageIds(channelNamesToIds[conditionLoadChannelName]!!)
        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(conditionType))
        assertEquals(1, patientMessages.size)
        assertEquals(2, conditionMessages.size)
    }
}
