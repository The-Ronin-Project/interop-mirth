package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalTime

const val patientDiscoverChannelName = "PatientDiscovery"

class PatientDiscoveryTest : BaseChannelTest(
    patientDiscoverChannelName,
    listOf("Location"),
    listOf("Patient", "Appointment", "Location")
) {
    private val patientType = "Patient"
    override val groupId = "interop-mirth-discovery"

    @Test
    fun `channel works`() {
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

            val newTenant = TenantClient.getTenant(it)
                .copy(availableStart = LocalTime.MIN, availableEnd = LocalTime.MAX)
            TenantClient.putTenant(newTenant)
            TenantClient.putMirthConfig(it, TenantClient.MirthConfig(locationIds = listOf(locationFhirId)))
        }

        deployAndStartChannel(false)
        waitForMessage(2)
        Assertions.assertTrue(KafkaWrapper.validateLoadEvents(2, ResourceType.PATIENT, groupId))
        val tenantConfig = TenantClient.getMirthConfig(tenantInUse)
        assertNotNull(tenantConfig.lastUpdated)
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
        deployAndStartChannel(true)
        Assertions.assertTrue(KafkaWrapper.validateLoadEvents(2, ResourceType.PATIENT))
    }

    @Test
    @Disabled // this case should be tested eventually but we don't have a great paradigm for it
    fun `channel works with multiple tenants`() {
    }

    @Test
    fun `no appointments no events`() {
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
        deployAndStartChannel(true)
        val events = KafkaWrapper.kafkaLoadService.retrieveLoadEvents(ResourceType.PATIENT)
        assertEquals(0, events.size)
    }

    @Test
    fun `channel kicks off dag`() {
        val channel = patientLoadChannelName
        val channelId = installChannel(channel)
        clearMessages(channelId)
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
        deployAndStartChannel(true)
        deployAndStartChannel(waitForMessage = true, channelToDeploy = channelId)
        stopChannel(channelId)

        Assertions.assertTrue(KafkaWrapper.validatePublishEvents(2, ResourceType.PATIENT, DataTrigger.NIGHTLY, groupId))
        // grab load events cause the channel has a different group id
        Assertions.assertTrue(KafkaWrapper.validateLoadEvents(2, ResourceType.PATIENT, groupId))
    }
}
