package com.projectronin.interop.mirth.channels

import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueStatus
import com.projectronin.interop.backfill.client.generated.models.NewBackfill
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
import com.projectronin.interop.mirth.channels.client.BackfillClient.backfillClient
import com.projectronin.interop.mirth.channels.client.BackfillClient.discoveryQueueClient
import com.projectronin.interop.mirth.channels.client.BackfillClient.queueClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.BACKFILL_DISCOVERY_QUEUE_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.ChannelMap
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.mirth.PATIENT_DISCOVERY_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BackfillDiscoveryQueueTest : BaseChannelTest(
    BACKFILL_DISCOVERY_QUEUE_CHANNEL_NAME,
    emptyList(),
    listOf("Patient", "Appointment", "Location"),
) {
    @BeforeEach
    fun `delete it all`() {
        // failed tests can leave a backfill id, so get all backfill ids and then delete them
        tenantsToTest().forEach {
            runBlocking {
                val backfills = backfillClient.getBackfills(it)
                backfills.forEach {
                    backfillClient.deleteBackfill(it.id)
                }
            }
        }
        clearMessages()
    }

    @Test
    fun `channel works`() {
        tenantsToTest().forEach {
            tenantInUse = it
            val location =
                location {
                    identifier of
                        listOf(
                            identifier {
                                system of "mockEHRDepartmentInternalSystem"
                                value of "${it}123"
                            },
                        )
                }
            val locationFhirId = MockEHRTestData.add(location)
            val patient1 =
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
                }
            val patient1Id = MockEHRTestData.add(patient1)

            val appointment1 =
                appointment {
                    status of "arrived"
                    participant of
                        listOf(
                            participant {
                                status of "accepted"
                                actor of reference("Patient", patient1Id)
                            },
                            participant {
                                status of "accepted"
                                actor of reference("Location", locationFhirId)
                            },
                        )
                    start of 2.daysFromNow()
                    end of 3.daysFromNow()
                }
            MockEHRTestData.add(appointment1)

            val aidboxLocation1 =
                location.copy(
                    identifier = location.identifier + tenantIdentifier(it) + fhirIdentifier(locationFhirId),
                )
            AidboxTestData.add(aidboxLocation1)

            runBlocking {
                backfillClient.postBackfill(
                    newBackfill = NewBackfill(it, listOf(locationFhirId), LocalDate.now(), LocalDate.now().plusDays(4)),
                )
            }
            val newTenant = TenantClient.getTenant(it)
            TenantClient.putTenant(newTenant)
        }

        // We need to stop the PatientDiscovery channel here to make sure it doesn't pick up our backfill and thus change the number of queue entries before we can verify.
        ChannelMap.installedDag[PATIENT_DISCOVERY_CHANNEL_NAME]?.let { // If it's not installed, we don't need to stop it.
            MirthClient.stopChannel(it)
        }

        MirthClient.deployChannel(testChannelId)
        startChannel(false)
        waitForMessage(2)
        tenantsToTest().forEach {
            val entries = runBlocking { discoveryQueueClient.getDiscoveryQueueEntries(it) }
            assertEquals(1, entries.size)
            assertEquals(DiscoveryQueueStatus.DISCOVERED, entries.first().status)
            val newQueues = runBlocking { queueClient.getQueueEntries(it) }
            assertEquals(1, newQueues.size)
        }
    }
}
