package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.daysAgo
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.ChannelMap
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.mirth.appointmentLoadChannelName
import com.projectronin.interop.mirth.channels.client.mirth.locationLoadChannelName
import com.projectronin.interop.mirth.channels.client.mirth.patientLoadChannelName
import com.projectronin.interop.mirth.channels.client.mirth.practitionerLoadChannelName
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds

// This is a special test case where we're testing the dag and making sure that everything
class BackfillTest : BaseChannelTest(
    patientLoadChannelName,
    listOf("Patient", "Appointment", "Location", "Practitioner"),
    listOf("Patient", "Appointment", "Location", "Practitioner")
) {

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `backfill test`(testTenant: String) {
        tenantInUse = testTenant
        val patientLoadTopic = KafkaClient.testingClient.loadTopic(ResourceType.Patient)
        val appointmentLoadTopic = KafkaClient.testingClient.loadTopic(ResourceType.Appointment)
        val locationLoadTopic = KafkaClient.testingClient.loadTopic(ResourceType.Location)
        val practitionerLoadTopic = KafkaClient.testingClient.loadTopic(ResourceType.Practitioner)

        val appointmentChannelID = ChannelMap.installedDag[appointmentLoadChannelName]!!
        val locationChannelID = ChannelMap.installedDag[practitionerLoadChannelName]!!
        val practitionerChannelId = ChannelMap.installedDag[locationLoadChannelName]!!
        MirthClient.clearChannelMessages(appointmentChannelID)
        MirthClient.clearChannelMessages(locationChannelID)
        MirthClient.clearChannelMessages(practitionerChannelId)

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
        }
        val patient1Id = MockEHRTestData.add(patient1)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)

        val fakeLocation1 = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "Location/1"
                }
            )
        }
        val locationFhirId1 = MockEHRTestData.add(fakeLocation1)

        MockOCIServerClient.createExpectations("Location", locationFhirId1, testTenant)

        val fakeLocation2 = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "Location/2"
                }
            )
        }
        val locationFhirId2 = MockEHRTestData.add(fakeLocation2)
        MockOCIServerClient.createExpectations("Location", locationFhirId2, testTenant)

        val fakePractitioner = practitioner {
            identifier of listOf(
                identifier {
                    system of "mockEHRProviderSystem"
                    value of "Provider/1"
                }
            )
        }
        val practitionerId = MockEHRTestData.add(fakePractitioner)
        MockOCIServerClient.createExpectations("Practitioner", practitionerId, testTenant)
        // because we're using the GetProvidersSchedule we actually have to resolve these locations against aidbox
        // so we're techinically doing a little cheating here in our test
        val aidboxLocation1 = fakeLocation1.copy(
            identifier = fakeLocation1.identifier + tenantIdentifier(testTenant) + fhirIdentifier(locationFhirId1)
        )
        val aidboxLocation2 = fakeLocation2.copy(
            identifier = fakeLocation2.identifier + tenantIdentifier(testTenant) + fhirIdentifier(locationFhirId2)
        )
        val aidboxPractitioner = fakePractitioner.copy(
            identifier = fakePractitioner.identifier + tenantIdentifier(testTenant) + fhirIdentifier(practitionerId)
        )
        AidboxTestData.add(aidboxLocation1)
        AidboxTestData.add(aidboxLocation2)
        AidboxTestData.add(aidboxPractitioner)

        val fakePractitioner2 = practitioner {
            identifier of listOf(
                identifier {
                    system of "mockEHRProviderSystem"
                    value of "Provider/1"
                }
            )
        }
        val practitionerId2 = MockEHRTestData.add(fakePractitioner2)

        val fakeAppointment1 = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patient1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId1)
                },
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", practitionerId)
                }
            )
            minutesDuration of 8
            start of 20.daysAgo()
            end of 20.daysAgo()
        }

        // shouldn't be found
        val fakeAppointment2 = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patient1Id)
                }
            )
            minutesDuration of 8
            start of 40.daysFromNow()
            end of 40.daysFromNow()
        }
        val fakeAppointment3 = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patient1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId2)
                },
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", practitionerId)
                }
            )
            minutesDuration of 8
            start of 360.daysAgo()
            end of 360.daysAgo()
        }

        // won't be found

        val fakeAppointment4 = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", patient1Id)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId2)
                },
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", practitionerId2)
                }
            )
            minutesDuration of 8
            start of 750.daysAgo()
            end of 750.daysAgo()
        }
        val appt1 = MockEHRTestData.add(fakeAppointment1)
        val appt2 = MockEHRTestData.add(fakeAppointment2)
        val appt3 = MockEHRTestData.add(fakeAppointment3)
        val appt4 = MockEHRTestData.add(fakeAppointment4)

        MockOCIServerClient.createExpectations("Appointment", appt1, "epicmock")
        MockOCIServerClient.createExpectations("Appointment", appt2, "epicmock")
        MockOCIServerClient.createExpectations("Appointment", appt3, "epicmock")
        MockOCIServerClient.createExpectations("Appointment", appt4, "epicmock")

        KafkaClient.testingClient.ensureStability(patientLoadTopic.topicName)
        KafkaClient.testingClient.ensureStability(appointmentLoadTopic.topicName)
        KafkaClient.testingClient.ensureStability(locationLoadTopic.topicName)
        KafkaClient.testingClient.ensureStability(practitionerLoadTopic.topicName)
        KafkaClient.testingClient.pushLoadEvent(
            testTenant,
            DataTrigger.BACKFILL,
            listOf(patient1Id),
            ResourceType.Patient,
            generateMetadata().copy(
                backfillRequest = Metadata.BackfillRequest(
                    backfillID = "123",
                    backfillStartDate = OffsetDateTime.now().minusYears(2),
                    backfillEndDate = OffsetDateTime.now()
                )
            )
        )
        waitForMessage(1)
        waitForMessage(2, channelID = appointmentChannelID)
        waitForMessage(1, channelID = locationChannelID)
        waitForMessage(1, channelID = practitionerChannelId)
        runBlocking { delay(2.seconds) }

        // we put in two at the start so we could resolve them, but they're gonna have different IDs
        assertEquals(4, getAidboxResourceCount("Location"))
        // same
        assertEquals(2, getAidboxResourceCount("Practitioner"))
        assertEquals(2, getAidboxResourceCount("Appointment"))
        assertEquals(1, getAidboxResourceCount("Patient"))
    }
}
