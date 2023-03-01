package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.period
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.dateTime
import com.projectronin.interop.fhir.generators.resources.encounter
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate

const val encounterLoadChannelName = "EncounterLoad"

class EncounterLoadTest : BaseChannelTest(
    encounterLoadChannelName,
    listOf("Patient", "Encounter"),
    listOf("Patient", "Encounter"),
    listOf(ResourceType.PATIENT, ResourceType.ENCOUNTER)
) {
    val nowish = LocalDate.now().minusDays(1)
    val laterish = nowish.plusDays(1)
    override val groupId = "interop-mirth-encounter"
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

        val encounter = encounter {
            subject of reference("Patient", patient1Id)
            period of period {
                start of dateTime {
                    year of nowish.year
                    month of nowish.monthValue
                    day of nowish.dayOfMonth
                }
                end of dateTime {
                    year of laterish.year
                    month of laterish.monthValue
                    day of laterish.dayOfMonth
                }
            }
            status of "planned"
            `class` of coding { display of "test" }
            type of listOf(codeableConcept { text of "type" })
        }

        val encounterId = MockEHRTestData.add(encounter)
        MockOCIServerClient.createExpectations("Encounter", encounterId, tenantInUse)
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(aidboxPatient),
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Encounter"))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.ENCOUNTER, DataTrigger.NIGHTLY)
        assertEquals(1, events.size)
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and encounters`(testTenant: String) {
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

        val encounter1 = encounter {
            subject of reference("Patient", patient1Id)
            period of period {
                start of dateTime {
                    year of nowish.year
                    month of nowish.monthValue
                    day of nowish.dayOfMonth
                }
            }
            status of "planned"
            `class` of coding { display of "test" }
            type of listOf(codeableConcept { text of "type" })
        }

        val encounter2 = encounter {
            subject of reference("Patient", patient2Id)
            period of period {
                start of dateTime {
                    year of nowish.year
                    month of nowish.monthValue
                    day of nowish.dayOfMonth
                }
            }
            status of "planned"
            `class` of coding { display of "test" }
            type of listOf(codeableConcept { text of "type" })
        }
        val encounter1ID = MockEHRTestData.add(encounter1)
        val encounter2ID = MockEHRTestData.add(encounter1)
        val encounter3ID = MockEHRTestData.add(encounter1)
        val encounter4ID = MockEHRTestData.add(encounter1)
        val encounter5ID = MockEHRTestData.add(encounter1)
        val encounter6ID = MockEHRTestData.add(encounter1)
        val encounterPat2ID = MockEHRTestData.add(encounter2)
        MockOCIServerClient.createExpectations("Encounter", encounter1ID, tenantInUse)
        MockOCIServerClient.createExpectations("Encounter", encounter2ID, tenantInUse)
        MockOCIServerClient.createExpectations("Encounter", encounter3ID, tenantInUse)
        MockOCIServerClient.createExpectations("Encounter", encounter4ID, tenantInUse)
        MockOCIServerClient.createExpectations("Encounter", encounter5ID, tenantInUse)
        MockOCIServerClient.createExpectations("Encounter", encounter6ID, tenantInUse)
        MockOCIServerClient.createExpectations("Encounter", encounterPat2ID, tenantInUse)
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(aidboxPatient1, aidboxPatient2),
        )

        // make sure MockEHR is OK
        var attempts = 0
        while (MockEHRClient.getAllResources("Encounter").size() < 7) {
            KotlinLogging.logger { }.info { MockEHRClient.getAllResources("Encounter").size() }
            runBlocking { delay(2000) }
            attempts++
            if (attempts > 5) break
        }

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount("Encounter"))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.ENCOUNTER, DataTrigger.AD_HOC)
        assertEquals(7, events.size)
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

        val encounter1 = encounter {
            subject of reference("Patient", patient1Id)
            period of period {
                start of dateTime {
                    year of nowish.year
                    month of nowish.monthValue
                    day of nowish.dayOfMonth
                }
            }
            status of "planned"
            `class` of coding { display of "test" }
            type of listOf(codeableConcept { text of "type" })
        }
        val encounterId = MockEHRTestData.add(encounter1)
        MockOCIServerClient.createExpectations("Encounter", encounterId, testTenant)
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(encounterId),
            resourceType = ResourceType.ENCOUNTER
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Encounter"))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.ENCOUNTER, DataTrigger.AD_HOC)
        assertEquals(1, events.size)
    }

    @Test
    fun `non-existent request errors`() {
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.ENCOUNTER
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
        assertEquals(0, getAidboxResourceCount("Encounter"))

        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.ENCOUNTER, DataTrigger.AD_HOC)
        assertEquals(0, events.size)
    }
}
