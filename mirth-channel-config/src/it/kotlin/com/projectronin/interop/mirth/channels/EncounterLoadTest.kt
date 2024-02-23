package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
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
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.ENCOUNTER_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.OffsetDateTime

class EncounterLoadTest : BaseChannelTest(
    ENCOUNTER_LOAD_CHANNEL_NAME,
    listOf("Patient", "Encounter"),
    listOf("Patient", "Encounter"),
) {
    val nowish = LocalDate.now().minusDays(1)
    val metadata1 =
        Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now(),
            targetedResources = emptyList(),
        )

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and encounters`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient { }
        val patient1Id = MockEHRTestData.add(patient1)

        val patient2 = patient { }
        val patient2Id = MockEHRTestData.add(patient2)

        val roninPatient1 =
            patient1.copy(
                id = Id("$tenantInUse-$patient1Id"),
                identifier = patient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id),
            )
        val roninPatient2 =
            patient2.copy(
                id = Id("$tenantInUse-$patient2Id"),
                identifier = patient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient2Id),
            )

        val encounter1 =
            encounter {
                subject of reference("Patient", patient1Id)
                period of
                    period {
                        start of
                            dateTime {
                                year of nowish.year
                                month of nowish.monthValue
                                day of nowish.dayOfMonth
                            }
                    }
                status of "planned"
                `class` of coding { display of "test" }
                type of
                    listOf(
                        codeableConcept {
                            text of "type"
                            coding of
                                listOf(
                                    coding {
                                        display of "display"
                                    },
                                )
                        },
                    )
            }

        val encounter2 =
            encounter {
                subject of reference("Patient", patient2Id)
                period of
                    period {
                        start of
                            dateTime {
                                year of nowish.year
                                month of nowish.monthValue
                                day of nowish.dayOfMonth
                            }
                    }
                status of "planned"
                `class` of coding { display of "test" }
                type of
                    listOf(
                        codeableConcept {
                            text of "type"
                            coding of
                                listOf(
                                    coding {
                                        display of "display"
                                    },
                                )
                        },
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

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2),
            metadata = metadata1,
        )
        waitForMessage(1)

        assertEquals(7, getAidboxResourceCount("Encounter"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient {}
        val patient1Id = MockEHRTestData.add(patient1)

        val encounter1 =
            encounter {
                subject of reference("Patient", patient1Id)
                period of
                    period {
                        start of
                            dateTime {
                                year of nowish.year
                                month of nowish.monthValue
                                day of nowish.dayOfMonth
                            }
                    }
                status of "planned"
                `class` of coding { display of "test" }
                type of
                    listOf(
                        codeableConcept {
                            text of "type"
                            coding of
                                listOf(
                                    coding {
                                        display of "display"
                                    },
                                )
                        },
                    )
            }
        val encounterId = MockEHRTestData.add(encounter1)
        MockOCIServerClient.createExpectations("Encounter", encounterId, testTenant)
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(encounterId),
            resourceType = ResourceType.Encounter,
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Encounter"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = TEST_TENANT,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.Encounter,
            metadata = metadata1,
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList, MirthResponseStatus.ERROR)
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount("Encounter"))
    }
}
