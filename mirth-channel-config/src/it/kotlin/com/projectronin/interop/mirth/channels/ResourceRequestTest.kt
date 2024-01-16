package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.event.interop.resource.request.v1.InteropResourceRequestV1
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
import com.projectronin.interop.mirth.channels.client.mirth.CONDITION_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.ChannelMap
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.mirth.PATIENT_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.RESOURCE_REQUEST_CHANNEL_NAME
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ResourceRequestTest : BaseChannelTest(
    RESOURCE_REQUEST_CHANNEL_NAME,
    listOf("Patient", "Condition"),
    listOf("Patient", "Condition"),
) {
    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag`(testTenant: String) {
        val conditionType = "Condition"
        val patientType = "Patient"

        val channels =
            listOf(
                PATIENT_LOAD_CHANNEL_NAME,
                CONDITION_LOAD_CHANNEL_NAME,
            )
        val channelNamesToIds =
            channels.associateWith {
                val id = ChannelMap.installedDag[it]!!
                MirthClient.clearChannelMessages(id)
                id
            }

        tenantInUse = testTenant
        val patient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // This is required to generate the Epic response.
                        },
                        name {
                            use of "official"
                        },
                    )
                gender of "male"
                telecom of emptyList()
            }
        val patientFhirId = MockEHRTestData.add(patient)
        MockOCIServerClient.createExpectations(patientType, patientFhirId, testTenant)

        val condition =
            condition {
                clinicalStatus of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                                    code of "active"
                                    display of "Active"
                                },
                            )
                        text of "Active"
                    }
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://terminology.hl7.org/CodeSystem/condition-category"
                                        code of "problem-list-item"
                                        display of "Problem list item"
                                    },
                                )
                            text of "Problem List Item"
                        },
                    )
                code of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://snomed.info/sct"
                                    code of "1023001"
                                    display of "Apnea"
                                },
                            )
                        text of "Apnea"
                    }
                subject of reference(patientType, "completelyDifferentPatient")
            }

        val conditionFhirId = MockEHRTestData.add(condition)
        MockOCIServerClient.createExpectations(conditionType, conditionFhirId, testTenant)

        // push event to get picked up
        KafkaClient.testingClient.kafkaRequestService.pushRequestEvent(
            testTenant,
            listOf(patientFhirId),
            ResourceType.Patient,
            "testing",
        )
        KafkaClient.testingClient.kafkaRequestService.pushRequestEvent(
            testTenant,
            listOf(conditionFhirId),
            ResourceType.Condition,
            "testing",
        )
        waitForMessage(2)
        waitForMessage(1, channelID = channelNamesToIds[PATIENT_LOAD_CHANNEL_NAME]!!)
        waitForMessage(2, channelID = channelNamesToIds[CONDITION_LOAD_CHANNEL_NAME]!!)

        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(conditionType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag when not processing downstream resources`(testTenant: String) {
        val conditionType = "Condition"
        val patientType = "Patient"

        val channels =
            listOf(
                PATIENT_LOAD_CHANNEL_NAME,
                CONDITION_LOAD_CHANNEL_NAME,
            )
        val channelNamesToIds =
            channels.associateWith {
                val id = ChannelMap.installedDag[it]!!
                MirthClient.clearChannelMessages(id)
                id
            }

        tenantInUse = testTenant
        val patient =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // This is required to generate the Epic response.
                        },
                        name {
                            use of "official"
                        },
                    )
                gender of "male"
                telecom of emptyList()
            }
        val patientFhirId = MockEHRTestData.add(patient)
        MockOCIServerClient.createExpectations(patientType, patientFhirId, testTenant)

        val condition =
            condition {
                clinicalStatus of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://terminology.hl7.org/CodeSystem/condition-clinical"
                                    code of "active"
                                    display of "Active"
                                },
                            )
                        text of "Active"
                    }
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://terminology.hl7.org/CodeSystem/condition-category"
                                        code of "problem-list-item"
                                        display of "Problem list item"
                                    },
                                )
                            text of "Problem List Item"
                        },
                    )
                code of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://snomed.info/sct"
                                    code of "1023001"
                                    display of "Apnea"
                                },
                            )
                        text of "Apnea"
                    }
                subject of reference(patientType, "completelyDifferentPatient")
            }

        val conditionFhirId = MockEHRTestData.add(condition)
        MockOCIServerClient.createExpectations(conditionType, conditionFhirId, testTenant)

        // push event to get picked up
        KafkaClient.testingClient.kafkaRequestService.pushRequestEvent(
            testTenant,
            listOf(patientFhirId),
            ResourceType.Patient,
            "testing",
            InteropResourceRequestV1.FlowOptions(
                disableDownstreamResources = true,
            ),
        )
        waitForMessage(1)
        waitForMessage(1, channelID = channelNamesToIds[PATIENT_LOAD_CHANNEL_NAME]!!)

        // Do this after the initial just to have given the patient time to have caused the condition, though we do not expect it to.
        KafkaClient.testingClient.kafkaRequestService.pushRequestEvent(
            testTenant,
            listOf(conditionFhirId),
            ResourceType.Condition,
            "testing",
            InteropResourceRequestV1.FlowOptions(
                disableDownstreamResources = true,
            ),
        )
        waitForMessage(2)
        waitForMessage(1, channelID = channelNamesToIds[PATIENT_LOAD_CHANNEL_NAME]!!)
        waitForMessage(1, channelID = channelNamesToIds[CONDITION_LOAD_CHANNEL_NAME]!!)

        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(conditionType))
    }
}
