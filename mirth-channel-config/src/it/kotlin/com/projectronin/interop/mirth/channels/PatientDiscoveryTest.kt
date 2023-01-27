package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.datatypes.participant
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.primitives.date
import com.projectronin.interop.mirth.channels.client.data.primitives.daysFromNow
import com.projectronin.interop.mirth.channels.client.data.resources.appointment
import com.projectronin.interop.mirth.channels.client.data.resources.location
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

const val patientDiscoverChannelName = "PatientDiscovery"

class PatientDiscoveryTest : BaseMirthChannelTest(
    patientDiscoverChannelName,
    listOf("Location"),
    listOf("Patient", "Appointment", "Location")
) {
    private val patientType = "Patient"

    @Test
    fun `channel works`() {
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
                }
            )
            gender of "male"
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
            identifier = location.identifier + tenantIdentifier(testTenant) + fhirIdentifier(locationFhirId)
        )

        AidboxTestData.add(aidboxLocation1)
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))

        deployAndStartChannel(true)
        val events = KafkaWrapper.kafkaLoadService.retrieveLoadEvents(ResourceType.PATIENT)
        assertEquals(1, events.size)
    }

    @Test
    @Disabled // INT-1398
    fun `channel works with multiple patients`() {
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
            identifier = location.identifier + tenantIdentifier(testTenant) + fhirIdentifier(locationFhirId)
        )

        AidboxTestData.add(aidboxLocation1)
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))

        deployAndStartChannel(true)
        val events = KafkaWrapper.kafkaLoadService.retrieveLoadEvents(ResourceType.PATIENT)
        assertEquals(2, events.size)
    }

    @Test
    @Disabled // this case should be tested eventually but we don't have a great paradigm for it
    fun `channel works with multiple tenants`() {
    }

    @Test
    fun `no appointments no events`() {
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
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))

        deployAndStartChannel(true)
        val events = KafkaWrapper.kafkaLoadService.retrieveLoadEvents(ResourceType.PATIENT)
        assertEquals(0, events.size)
    }
}
