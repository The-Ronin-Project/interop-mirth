package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.codeableConcept
import com.projectronin.interop.mirth.channels.client.data.datatypes.coding
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.primitives.date
import com.projectronin.interop.mirth.channels.client.data.resources.observation
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

const val observationLoadChannelName = "ObservationLoad"

class ObservationLoadTest : BaseChannelTest(
    observationLoadChannelName,
    listOf("Patient", "Observation"),
    listOf("Patient", "Observation"),
    listOf(ResourceType.PATIENT, ResourceType.CONDITION)
) {
    val patientType = "Patient"
    val observationType = "Observation"
    override val groupId = "interop-mirth-observation"

    @ParameterizedTest
    @ValueSource(strings = ["epicmock"]) // INT-1376 @MethodSource("tenantsToTest")
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

        val observation = observation {
            subject of reference(patientType, patient1Id)
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
        val obsvId = MockEHRTestData.add(observation)

        MockOCIServerClient.createExpectations(observationType, obsvId)

        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(aidboxPatient),
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(observationType))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(
            ResourceType.OBSERVATION, DataTrigger.NIGHTLY, groupId
        )
        assertEquals(1, events.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["epicmock"]) // INT-1376 @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and observations`(testTenant: String) {
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

        val observation1 = observation {
            subject of reference(patientType, patient1Id)
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

        val observation2 = observation {
            subject of reference(patientType, patient2Id)
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
        val observation1ID = MockEHRTestData.add(observation1)
        val observation2ID = MockEHRTestData.add(observation1)
        val observation3ID = MockEHRTestData.add(observation1)
        val observation4ID = MockEHRTestData.add(observation1)
        val observation5ID = MockEHRTestData.add(observation1)
        val observation6ID = MockEHRTestData.add(observation1)
        val observationPat2ID = MockEHRTestData.add(observation2)
        MockOCIServerClient.createExpectations(observationType, observation1ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation2ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation3ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation4ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation5ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observation6ID, tenantInUse)
        MockOCIServerClient.createExpectations(observationType, observationPat2ID, tenantInUse)
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(aidboxPatient1, aidboxPatient2),
        )

        // make sure MockEHR is OK
        var attempts = 0
        while (MockEHRClient.getAllResources(observationType).size() < 7) {
            KotlinLogging.logger { }.info { MockEHRClient.getAllResources(observationType).size() }
            runBlocking { delay(2000) }
            attempts++
            if (attempts > 5) break
        }

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount(observationType))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.OBSERVATION, DataTrigger.AD_HOC, groupId)
        assertEquals(7, events.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["epicmock"]) // INT-1376 @MethodSource("tenantsToTest")
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

        val observation = observation {
            subject of reference(patientType, patient1Id)
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
        val observationID = MockEHRTestData.add(observation)
        MockOCIServerClient.createExpectations(observationType, observationID, testTenant)
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(observationID),
            resourceType = ResourceType.OBSERVATION
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(observationType))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(
            ResourceType.OBSERVATION, DataTrigger.AD_HOC, groupId
        )
        assertEquals(1, events.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["epicmock"]) // INT-1376 @MethodSource("tenantsToTest")
    fun `non-existant request errors`() {
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.OBSERVATION
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
        assertEquals(0, getAidboxResourceCount(observationType))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(
            ResourceType.OBSERVATION, DataTrigger.AD_HOC, groupId
        )
        assertEquals(0, events.size)
    }
}
