package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.period
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.dateTime
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.encounter
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate

const val encounterLoadChannelName = "EncounterLoad"

class EncounterLoadTest : BaseChannelTest(
    encounterLoadChannelName,
    listOf("Patient", "Encounter"),
    listOf("Patient", "Encounter")
) {
    val nowish = LocalDate.now().minusDays(1)
    val laterish = nowish.plusDays(1)

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {}
        val patient1Id = MockEHRTestData.add(patient1)
        val roninPatientId = "$tenantInUse-$patient1Id"
        val roninPatient = patient1.copy(
            id = Id(roninPatientId),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )

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
            type of listOf(
                codeableConcept {
                    text of "type"
                    coding of listOf(
                        coding {
                            display of "display"
                        }
                    )
                }
            )
        }

        val encounterId = MockEHRTestData.add(encounter)
        MockOCIServerClient.createExpectations("Encounter", encounterId, tenantInUse)
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(roninPatient)
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Encounter"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and encounters`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient { }
        val patient1Id = MockEHRTestData.add(patient1)

        val patient2 = patient { }
        val patient2Id = MockEHRTestData.add(patient2)

        val roninPatient1 = patient1.copy(
            id = Id("$tenantInUse-$patient1Id"),
            identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )
        val roninPatient2 = patient2.copy(
            id = Id("$tenantInUse-$patient2Id"),
            identifier = patient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient2Id)
        )

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
            type of listOf(
                codeableConcept {
                    text of "type"
                    coding of listOf(
                        coding {
                            display of "display"
                        }
                    )
                }
            )
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
            type of listOf(
                codeableConcept {
                    text of "type"
                    coding of listOf(
                        coding {
                            display of "display"
                        }
                    )
                }
            )
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
        MockEHRTestData.validateAll()

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2)
        )

        waitForMessage(2)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount("Encounter"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {}
        val patient1Id = MockEHRTestData.add(patient1)

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
            type of listOf(
                codeableConcept {
                    text of "type"
                    coding of listOf(
                        coding {
                            display of "display"
                        }
                    )
                }
            )
        }
        val encounterId = MockEHRTestData.add(encounter1)
        MockOCIServerClient.createExpectations("Encounter", encounterId, testTenant)
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(encounterId),
            resourceType = ResourceType.Encounter
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Encounter"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.Encounter
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        messageList.forEach { ids ->
            val message = MirthClient.getMessageById(testChannelId, ids)
            message.destinationMessages.forEach {
                assertEquals("ERROR", it.status)
            }
        }
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount("Encounter"))
    }
}
