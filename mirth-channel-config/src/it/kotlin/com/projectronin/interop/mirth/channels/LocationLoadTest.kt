package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.location
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

const val locationLoadChannelName = "LocationLoad"

class LocationLoadTest : BaseChannelTest(
    locationLoadChannelName,
    listOf("Appointment", "Location"),
    listOf("Appointment", "Location")
) {

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works`(testTenant: String) {
        tenantInUse = testTenant
        val fakeLocation = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "Location/1"
                }
            )
        }
        val locationFhirId = MockEHRTestData.add(fakeLocation)
        val fakeAppointment = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        val fakeAppointmentId = MockEHRTestData.add(fakeAppointment)

        val fakeAidboxApptId = "$tenantInUse-$fakeAppointmentId"
        val fakeAidboxAppt = fakeAppointment.copy(
            id = Id(fakeAidboxApptId),
            identifier = fakeAppointment.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakeAppointmentId)
        )
        AidboxTestData.add(fakeAidboxAppt)
        MockOCIServerClient.createExpectations("Location", locationFhirId, tenantInUse)
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxAppt)
        )
        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Location"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple locations and appointments`(testTenant: String) {
        tenantInUse = testTenant
        val fakeLocation1 = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "Location/1"
                }
            )
        }
        val locationFhirId1 = MockEHRTestData.add(fakeLocation1)
        val fakeLocation2 = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "Location/2"
                }
            )
        }
        val locationFhirId2 = MockEHRTestData.add(fakeLocation2)

        val fakeAppointment1 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId1)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        val appt1Id = MockEHRTestData.add(fakeAppointment1)
        val aidboxAppt1Id = "$tenantInUse-$appt1Id"
        val aidboxAppt1 = fakeAppointment1.copy(
            id = Id(aidboxAppt1Id),
            identifier = fakeAppointment1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(appt1Id)
        )
        AidboxTestData.add(aidboxAppt1)
        val fakeAppointment2 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId2)
                },
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId2) // same location twice
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        val appt2Id = MockEHRTestData.add(fakeAppointment2)
        val aidboxAppt2Id = "$tenantInUse-$appt2Id"
        val aidboxAppt2 = fakeAppointment2.copy(
            id = Id(aidboxAppt2Id),
            identifier = fakeAppointment2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(appt2Id)
        )
        AidboxTestData.add(aidboxAppt2)

        val location1ID = MockEHRTestData.add(fakeLocation1)
        val location2ID = MockEHRTestData.add(fakeLocation1)
        val location3ID = MockEHRTestData.add(fakeLocation1)
        val location4ID = MockEHRTestData.add(fakeLocation1)
        val location5ID = MockEHRTestData.add(fakeLocation1)
        val location6ID = MockEHRTestData.add(fakeLocation1)
        val location7ID = MockEHRTestData.add(fakeLocation2)
        MockOCIServerClient.createExpectations("Location", location1ID, tenantInUse)
        MockOCIServerClient.createExpectations("Location", location2ID, tenantInUse)
        MockOCIServerClient.createExpectations("Location", location3ID, tenantInUse)
        MockOCIServerClient.createExpectations("Location", location4ID, tenantInUse)
        MockOCIServerClient.createExpectations("Location", location5ID, tenantInUse)
        MockOCIServerClient.createExpectations("Location", location6ID, tenantInUse)
        MockOCIServerClient.createExpectations("Location", location7ID, tenantInUse)

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(aidboxAppt1, aidboxAppt2)
        )

        // make sure MockEHR is OK
        MockEHRTestData.validateAll()

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(2, messageList.size)
        assertEquals(2, getAidboxResourceCount("Location"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val fakeLocation1 = location {
            identifier of listOf(
                identifier {
                    system of "mockEHRDepartmentInternalSystem"
                    value of "Location/1"
                }
            )
        }
        val locationFhirId1 = MockEHRTestData.add(fakeLocation1)
        val fakeAppointment1 = appointment {
            status of "arrived"
            participant of listOf(
                participant {
                    status of "accepted"
                    actor of reference("Location", locationFhirId1)
                }
            )
            start of 2.daysFromNow()
            end of 3.daysFromNow()
        }
        val appt1Id = MockEHRTestData.add(fakeAppointment1)
        val aidboxAppt1Id = "$tenantInUse-$appt1Id"
        val aidboxAppt2 = fakeAppointment1.copy(
            id = Id(aidboxAppt1Id),
            identifier = fakeAppointment1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(appt1Id)
        )
        AidboxTestData.add(aidboxAppt2)
        MockOCIServerClient.createExpectations("Location", locationFhirId1, testTenant)
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(locationFhirId1),
            resourceType = ResourceType.Location
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Location"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("nothing to see here"),
            resourceType = ResourceType.Location
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
        assertEquals(0, getAidboxResourceCount("Location"))
    }
}
