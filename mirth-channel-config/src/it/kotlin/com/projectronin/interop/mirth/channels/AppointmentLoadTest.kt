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
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

const val appointmentLoadChannelName = "AppointmentLoad"

class AppointmentLoadTest : BaseChannelTest(
    appointmentLoadChannelName,
    listOf("Patient", "Appointment", "Location"),
    listOf("Patient", "Appointment", "Location"),
) {
    override val groupId = "interop-mirth-appointment"

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
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient)
        )

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Appointment"))

        assertTrue(
            KafkaWrapper.validatePublishEvents(
                1,
                ResourceType.APPOINTMENT, DataTrigger.NIGHTLY, groupId
            )
        )
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
        MockOCIServerClient.createExpectations("Appointment", appt1, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt2, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt3, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt4, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt5, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", appt6, tenantInUse)
        MockOCIServerClient.createExpectations("Appointment", patientAppt, tenantInUse)
        KafkaWrapper.kafkaPublishService.publishResources(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(fakeAidboxPatient1, fakeAidboxPatient2),
        )

        // make sure MockEHR is OK
        MockEHRTestData.validateAll()

        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(7, getAidboxResourceCount("Appointment"))

        assertAllConnectorsSent(messageList)

        assertTrue(
            KafkaWrapper.validatePublishEvents(
                7,
                ResourceType.APPOINTMENT,
                DataTrigger.AD_HOC,
                groupId
            )
        )
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
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeAppointmentId),
            resourceType = ResourceType.APPOINTMENT
        )
        deployAndStartChannel(true)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Appointment"))

        assertTrue(
            KafkaWrapper.validatePublishEvents(
                1,
                ResourceType.APPOINTMENT, DataTrigger.AD_HOC, groupId
            )
        )
    }

    @Test
    fun `nothing found request results in error`() {
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("nothing to see here"),
            resourceType = ResourceType.APPOINTMENT
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
        assertEquals(0, getAidboxResourceCount("Appointment"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with dag`(testTenant: String) {

        val appointmentType = "Appointment"
        val locationType = "Location"
        val types = listOf(
            appointmentType,
            locationType
        )

        val channels = listOf(
            locationLoadChannelName
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

        val fakeAppointment = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference(locationType, locationId)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        val appointmentId = MockEHRTestData.add(fakeAppointment)
        MockOCIServerClient.createExpectations(appointmentType, appointmentId, testTenant)

        // push event to get picked up
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(appointmentId),
            ResourceType.APPOINTMENT
        )
        deployAndStartChannel(true)
        channelIds.forEach {
            deployAndStartChannel(
                waitForMessage = true,
                channelToDeploy = it
            )
            stopChannel(it)
        }

        // check that publish event was triggered
        assertTrue(
            KafkaWrapper.validatePublishEvents(
                1,
                ResourceType.LOCATION, DataTrigger.NIGHTLY, groupId
            )
        )
        assertTrue(
            KafkaWrapper.validatePublishEvents(
                1,
                ResourceType.APPOINTMENT, DataTrigger.NIGHTLY, groupId
            )
        )

        types.forEach {
            assertEquals(1, getAidboxResourceCount(it))
        }
    }
}
