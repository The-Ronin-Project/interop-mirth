package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.backfill.client.generated.models.BackfillStatus
import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueStatus
import com.projectronin.interop.backfill.client.generated.models.NewBackfill
import com.projectronin.interop.backfill.client.generated.models.NewQueueEntry
import com.projectronin.interop.backfill.client.generated.models.UpdateDiscoveryEntry
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.BackfillClient
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.ChannelMap
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.mirth.patientDiscoverChannelName
import com.projectronin.interop.mirth.channels.client.mirth.patientLoadChannelName
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds

class PatientDiscoveryTest : BaseChannelTest(
    patientDiscoverChannelName,
    listOf("Location", "Patient", "Appointment"),
    listOf("Patient", "Appointment", "Location")
) {
    private val patientType = "Patient"

    @BeforeEach
    fun `delete it all`() {
        // failed tests can leave a backfill id, so get all backfill ids and then delete them
        tenantsToTest().forEach {
            runBlocking {
                val backfills = BackfillClient.backfillClient.getBackfills(it)
                backfills.forEach {
                    BackfillClient.backfillClient.deleteBackfill(it.id)
                }
            }
        }
    }

    @Test
    fun `channel works with multiple patients`() {
        tenantsToTest().forEach {
            tenantInUse = it
            val location = location {
                identifier of listOf(
                    identifier {
                        system of "mockEHRDepartmentInternalSystem"
                        value of "${it}123"
                    }
                )
            }
            val locationFhirId = MockEHRTestData.add(location)
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

            val appointment1 = appointment {
                status of "arrived"
                participant of listOf(
                    participant {
                        status of "accepted"
                        actor of reference(patientType, patient2Id)
                    },
                    participant {
                        status of "accepted"
                        actor of reference("Location", locationFhirId)
                    }
                )
                start of 2.daysFromNow()
                end of 3.daysFromNow()
            }

            val appointment2 = appointment {
                status of "arrived"
                participant of listOf(
                    participant {
                        status of "accepted"
                        actor of reference(patientType, patient1Id)
                    },
                    participant {
                        status of "accepted"
                        actor of reference("Location", locationFhirId)
                    }
                )
                start of 2.daysFromNow()
                end of 3.daysFromNow()
            }
            MockEHRTestData.add(appointment1)
            MockEHRTestData.add(appointment2)

            val aidboxLocation1 = location.copy(
                identifier = location.identifier + tenantIdentifier(it) + fhirIdentifier(locationFhirId)
            )
            AidboxTestData.add(aidboxLocation1)

            val newTenant = TenantClient.getTenant(it)
                .copy(availableStart = LocalTime.MIN, availableEnd = LocalTime.MAX)
            TenantClient.putTenant(newTenant)
            TenantClient.putMirthConfig(it, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))
        }

        MirthClient.deployChannel(testChannelId)
        startChannel(true)
        val messages = getChannelMessageIds()
        assertAllConnectorsStatus(messages)
    }

    @Test
    fun `no appointments no events`() {
        KafkaClient.testingClient.reset()
        tenantsToTest().forEach {
            tenantInUse = it
            val location = location {
                identifier of listOf(
                    identifier {
                        system of "mockEHRDepartmentInternalSystem"
                        value of "123"
                    }
                )
            }
            val locationFhirId = MockEHRTestData.add(location)
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
                    }
                )
                gender of "male"
            }
            MockEHRTestData.add(patient)

            val aidboxLocation1 = location.copy(
                identifier = location.identifier + tenantIdentifier(testTenant) + fhirIdentifier(locationFhirId)
            )

            AidboxTestData.add(aidboxLocation1)
            val newTenant = TenantClient.getTenant(it)
                .copy(availableStart = LocalTime.MIN, availableEnd = LocalTime.MAX)
            TenantClient.putTenant(newTenant)
            TenantClient.putMirthConfig(it, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))
        }
        MirthClient.deployChannel(testChannelId)
        startChannel(true)
        val events = KafkaClient.testingClient.kafkaLoadService.retrieveLoadEvents(ResourceType.Patient)
        assertEquals(0, events.size)
    }

    @Test
    fun `channel kicks off dag`() {
        KafkaClient.testingClient.reset()

        val patientChannelId = ChannelMap.installedDag[patientLoadChannelName]!!
        MirthClient.clearChannelMessages(patientChannelId)
        tenantsToTest().forEach {
            tenantInUse = it

            val location = location {
                identifier of listOf(
                    identifier {
                        system of "mockEHRDepartmentInternalSystem"
                        value of "123"
                    }
                )
            }
            val locationFhirId = MockEHRTestData.add(location)
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
                    },
                    name {
                        use of "official"
                    }
                )
                gender of "male"
                telecom of emptyList()
            }
            val patient1Id = MockEHRTestData.add(patient1)

            val appointment1 = appointment {
                status of "arrived"
                participant of listOf(
                    participant {
                        status of "accepted"
                        actor of reference(patientType, patient1Id)
                    },
                    participant {
                        status of "accepted"
                        actor of reference("Location", locationFhirId)
                    }
                )
                start of 2.daysFromNow()
                end of 3.daysFromNow()
            }
            MockEHRTestData.add(appointment1)

            val aidboxLocation1 = location.copy(
                identifier = location.identifier + tenantIdentifier(it) + fhirIdentifier(locationFhirId)
            )

            AidboxTestData.add(aidboxLocation1)
            TenantClient.putMirthConfig(it, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))
            MockOCIServerClient.createExpectations("patient", patient1Id, it)
            val newTenant = TenantClient.getTenant(it)
                .copy(availableStart = LocalTime.MIN, availableEnd = LocalTime.MAX)
            TenantClient.putTenant(newTenant)
            TenantClient.putMirthConfig(it, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))
        }
        MirthClient.deployChannel(testChannelId)
        startChannel()
        waitForMessage(1, channelID = patientChannelId)

        assertEquals(1, getAidboxResourceCount(patientType))
    }

    @Test
    fun `backfill kicks off dag`() {
        KafkaClient.testingClient.reset()
        val patientChannelId = ChannelMap.installedDag[patientLoadChannelName]!!
        MirthClient.clearChannelMessages(patientChannelId)
        tenantsToTest().forEach {
            tenantInUse = it
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
                    },
                    name {
                        use of "official"
                    }
                )
                gender of "male"
                telecom of emptyList()
            }
            val patient1Id = MockEHRTestData.add(patient1)

            runBlocking {
                // make a new backfill
                val backfillId = BackfillClient.backfillClient.postBackfill(
                    newBackfill = NewBackfill(
                        tenantInUse,
                        listOf("123"),
                        startDate = LocalDate.now().minusYears(1),
                        endDate = LocalDate.now()
                    )
                ).id!!
                // complete the undiscovered queues so they don't accidentally resolve the and add the queues
                val undiscovereEntries = BackfillClient.discoveryQueueClient.getDiscoveryQueueEntries(testTenant)
                undiscovereEntries.forEach {
                    BackfillClient.discoveryQueueClient.updateDiscoveryQueueEntryByID(
                        it.id,
                        UpdateDiscoveryEntry(
                            DiscoveryQueueStatus.DISCOVERED
                        )
                    )
                }
                BackfillClient.queueClient.postQueueEntry(backfillId, listOf(NewQueueEntry(backfillId, patient1Id)))
            }

            TenantClient.putMirthConfig(
                it,
                TenantClient.MirthConfig(
                    locationIds = listOf("123"),
                    // make it think we just ran the nightly load
                    lastUpdated = OffsetDateTime.now()
                )
            )
            MockOCIServerClient.createExpectations("patient", patient1Id, it)
            val newTenant = TenantClient.getTenant(it)
                .copy(availableStart = LocalTime.MIN, availableEnd = LocalTime.MAX)
            TenantClient.putTenant(newTenant)
            TenantClient.putMirthConfig(
                it,
                TenantClient.MirthConfig(
                    locationIds = listOf("123"),
                    // make it think we just ran the nightly load
                    lastUpdated = OffsetDateTime.now()
                )
            )
        }
        MirthClient.deployChannel(testChannelId)
        startChannel()

        waitForMessage(1, channelID = patientChannelId)

        runBlocking { delay(5.seconds) }

        // we only do one backfill event at a time
        MirthClient.deployChannel(testChannelId)
        startChannel()
        waitForMessage(2, channelID = patientChannelId)

        assertEquals(2, tenantsToTest().mapToInt { getAidboxResourceCount("Patient", it) }.sum())
        tenantsToTest().forEach {
            runBlocking {
                val backfills = BackfillClient.backfillClient.getBackfills(it)
                assertEquals(1, backfills.size)
                assertEquals(BackfillStatus.STARTED, backfills.first().status)

                val queueEntries = BackfillClient.queueClient.getEntriesByBackfillID(backfills.first().id)
                assertEquals(1, queueEntries.size)
                assertEquals(BackfillStatus.STARTED, queueEntries.first().status)
            }
        }
    }

    @Test
    fun `channel works with clinical trial patients`() {
        KafkaClient.testingClient.reset()
        MockOCIServerClient.client.clear(HttpRequest.request().withMethod("GET").withPath("/subjects"))

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
        val tenantSubjectJson =
            tenantsToTest().toArray().joinToString(",") { """{ "roninFhirId" : "$it-$patient1Id" }""" }

        MockOCIServerClient.client.`when`(
            HttpRequest.request()
                .withMethod("GET")
                .withPath("/subjects")
                .withQueryStringParameter("activeIdsOnly", "true"),
            Times.exactly(2)
        ).respond(
            HttpResponse.response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody("[$tenantSubjectJson]")
        )

        tenantsToTest().forEach {
            tenantInUse = it

            val newTenant = TenantClient.getTenant(it)
                .copy(availableStart = LocalTime.MIN, availableEnd = LocalTime.MAX)
            TenantClient.putTenant(newTenant)
            TenantClient.putMirthConfig(it, TenantClient.MirthConfig(locationIds = listOf("ClinicalTrialLoadLocation")))
        }

        MirthClient.deployChannel(testChannelId)
        startChannel()
        waitForMessage(2, channelID = testChannelId)
        val messages = getChannelMessageIds()
        assertAllConnectorsStatus(messages)

        tenantsToTest().forEach { tenant ->
            val connector = messages.flatMap { it.connectorMessages.entry }.singleOrNull { connector ->
                connector.connectorMessage.connectorName == "Request Patients" && connector.connectorMessage.metaDataMap?.entry?.any { e ->
                    e.string == listOf(
                        "TENANT",
                        tenant
                    )
                } == true
            }

            assertEquals("[\"Patient/$patient1Id\"]", connector?.connectorMessage?.raw?.content)
        }
    }
}
