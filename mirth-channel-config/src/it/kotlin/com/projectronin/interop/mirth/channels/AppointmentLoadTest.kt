package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
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
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
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
import java.time.OffsetDateTime
import java.time.ZoneOffset

const val appointmentLoadChannelName = "AppointmentLoad"

class AppointmentLoadTest : BaseChannelTest(
    appointmentLoadChannelName,
    listOf("Patient", "Appointment", "Location"),
    listOf("Patient", "Appointment", "Location")
) {

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works nightly`(testTenant: String) {
        tenantInUse = testTenant
        val startDate = 2.daysFromNow()
        val endDate = 3.daysFromNow()
        val fakePatient = patient {
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
                    use of "usual" // required
                }
            )
            gender of "male"
        }

        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$tenantInUse-$fakePatientId"
        val fakeAidboxPatient = fakePatient.copy(
            id = Id(fakeAidboxPatientId),
            identifier = fakePatient.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatientId)
        )
        AidboxTestData.add(fakeAidboxPatient)

        val fakeAppointment = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", fakePatientId)
                }
            )
            minutesDuration of 8
            start of startDate
            end of endDate
        }
        val fakeAppointmentId = MockEHRTestData.add(fakeAppointment)
        MockOCIServerClient.createExpectations("Appointment", fakeAppointmentId, tenantInUse)

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient)
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Appointment"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and appointments nightly`(testTenant: String) {
        tenantInUse = testTenant
        val startDate = 2.daysFromNow()
        val endDate = 3.daysFromNow()
        val fakePatient1 = patient {
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
                    use of "usual" // required
                }
            )
            gender of "male"
        }
        val fakePatient2 = patient {
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
                    use of "usual" // required
                }
            )
            gender of "male"
        }
        val fakePatient1Id = MockEHRTestData.add(fakePatient1)
        val fakePatient2Id = MockEHRTestData.add(fakePatient2)
        val fakeAidboxPatient1Id = "$tenantInUse-$fakePatient1Id"
        val fakeAidboxPatient2Id = "$tenantInUse-$fakePatient2Id"
        val fakeAidboxPatient1 = fakePatient1.copy(
            id = Id(fakeAidboxPatient1Id),
            identifier = fakePatient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatient1Id)
        )
        val fakeAidboxPatient2 = fakePatient2.copy(
            id = Id(fakeAidboxPatient2Id),
            identifier = fakePatient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatient2Id)
        )
        AidboxTestData.add(fakeAidboxPatient1)
        AidboxTestData.add(fakeAidboxPatient2)

        val fakeAppointment1 = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", fakePatient1Id)
                }
            )
            minutesDuration of 8
            start of startDate
            end of endDate
        }

        val fakeAppointment2 = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", fakePatient2Id)
                }
            )
            minutesDuration of 8
            start of startDate
            end of endDate
        }
        val appt1 = MockEHRTestData.add(fakeAppointment1)
        val appt2 = MockEHRTestData.add(fakeAppointment1)
        val appt3 = MockEHRTestData.add(fakeAppointment1)
        val appt4 = MockEHRTestData.add(fakeAppointment1)
        val appt5 = MockEHRTestData.add(fakeAppointment1)
        val appt6 = MockEHRTestData.add(fakeAppointment1)
        val patientAppt = MockEHRTestData.add(fakeAppointment2)
        // make sure MockEHR is OK
        MockEHRTestData.validateAll()

        MockOCIServerClient.createExpectations("Appointment", appt1, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt2, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt3, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt4, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt5, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt6, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", patientAppt, tenantInUse)

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(fakeAidboxPatient1, fakeAidboxPatient2)
        )

        waitForMessage(2)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount("Appointment"))

        assertAllConnectorsSent(messageList)
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val startDate = 2.daysFromNow()
        val endDate = 3.daysFromNow()
        val fakePatient = patient {
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
                    use of "usual" // required
                }
            )
            gender of "male"
        }
        val fakePatientId = MockEHRTestData.add(fakePatient)
        val fakeAidboxPatientId = "$testTenant-$fakePatientId"
        val fakeAidboxPatient = fakePatient.copy(
            id = Id(fakeAidboxPatientId),
            identifier = fakePatient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(fakePatientId)
        )
        AidboxTestData.add(fakeAidboxPatient)

        val fakeAppointment = appointment {
            status of "pending"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Patient", fakePatientId)
                }
            )
            minutesDuration of 8
            start of startDate
            end of endDate
        }
        val fakeAppointmentId = MockEHRTestData.add(fakeAppointment)
        MockOCIServerClient.createExpectations("Appointment", fakeAppointmentId, testTenant)

        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeAppointmentId),
            resourceType = ResourceType.Appointment
        )
        waitForMessage(1)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Appointment"))
    }

    @Test
    fun `nothing found request results in error`() {
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("nothing to see here"),
            resourceType = ResourceType.Appointment
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
        assertEquals(0, getAidboxResourceCount("Appointment"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag`(testTenant: String) {
        val appointmentPublishTopics = KafkaClient.publishTopics(ResourceType.Appointment)
        val appointmentType = "Appointment"
        val locationType = "Location"
        val types = listOf(
            appointmentType,
            locationType
        )

        val channels = listOf(
            locationLoadChannelName,
            practitionerLoadChannelName
        )
        val channelIds = channels.map {
            val id = installChannel(it)
            clearMessages(id)
            id
        }

        tenantInUse = testTenant
        val fakeLocation = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "Location/1"
                }
            )
        }
        val locationId = MockEHRTestData.add(fakeLocation)
        MockOCIServerClient.createExpectations(locationType, locationId, testTenant)

        val fakePractitioner = practitioner {}
        val practitionerId = MockEHRTestData.add(fakePractitioner)
        MockOCIServerClient.createExpectations("Practitioner", practitionerId, testTenant)

        val fakeAppointment = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference(locationType, locationId)
                },
                participant {
                    status of "accepted"
                    actor of reference("Practitioner", practitionerId)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
            minutesDuration of 60
        }
        val appointmentId = MockEHRTestData.add(fakeAppointment)
        MockOCIServerClient.createExpectations(appointmentType, appointmentId, testTenant)

        // deploy dag channels
        channelIds.forEach {
            deployAndStartChannel(channelToDeploy = it)
        }
        appointmentPublishTopics.forEach {
            KafkaClient.ensureStability(it.topicName)
        }
        // push event to get picked up
        val metadata = Metadata(runId = "appointment1", runDateTime = OffsetDateTime.now(ZoneOffset.UTC))
        KafkaClient.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(appointmentId),
            ResourceType.Appointment,
            metadata
        )
        val appointmentPublishTopic =
            KafkaClient.publishTopics(ResourceType.Appointment).first { it.topicName.contains("nightly") }
        KafkaClient.ensureStability(appointmentPublishTopic.topicName)
        waitForMessage(1)
        channelIds.forEach {
            waitForMessage(1, channelID = it)
            stopChannel(it)
        }
        types.forEach {
            assertEquals(1, getAidboxResourceCount(it))
        }
    }
}
