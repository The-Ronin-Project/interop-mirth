package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalTime

class PatientDiscoveryTest : BaseChannelTest(
    patientDiscoverChannelName,
    listOf("Location", "Patient", "Appointment"),
    listOf("Patient", "Appointment", "Location")
) {
    private val patientType = "Patient"

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
}
