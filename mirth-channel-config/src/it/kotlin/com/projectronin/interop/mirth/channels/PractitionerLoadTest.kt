package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.mirth.PRACTITIONER_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime

@Suppress("ktlint:standard:max-line-length")
class PractitionerLoadTest : BaseChannelTest(
    PRACTITIONER_LOAD_CHANNEL_NAME,
    listOf("Appointment", "Practitioner"),
    listOf("Appointment", "Practitioner"),
) {
    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `repeat appointments are ignored`(testTenant: String) {
        tenantInUse = testTenant

        // mock: patient at the EHR got published to Ronin
        val fakePatient = patient {}
        val fakePatientId = MockEHRTestData.add(fakePatient)

        // mock: practitioner at the EHR
        val fakePractitioner = practitioner { }
        val fakePractitionerId = MockEHRTestData.add(fakePractitioner)

        // mock: appointment at the EHR got published to Ronin
        val startDate = 2.daysFromNow()
        val endDate = 3.daysFromNow()
        val fakeAppointment =
            appointment {
                status of "pending"
                participant of
                    listOf(
                        participant {
                            status of "accepted"
                            actor of reference("Patient", fakePatientId)
                        },
                        participant {
                            status of "accepted"
                            actor of reference("Practitioner", fakePractitionerId)
                        },
                    )
                minutesDuration of 8
                start of startDate
                end of endDate
            }
        val fakeAppointmentId = MockEHRTestData.add(fakeAppointment)
        val fakeAidboxAppointmentId = "$tenantInUse-$fakeAppointmentId"
        val fakeAidboxAppointment =
            fakeAppointment.copy(
                id = Id(fakeAidboxAppointmentId),
                identifier =
                    fakeAppointment.identifier + tenantIdentifier(tenantInUse) +
                        fhirIdentifier(
                            fakeAppointmentId,
                        ),
                participant =
                    fakeAppointment.participant.map { participant ->
                        if (participant.actor?.reference?.value?.contains("Practitioner") == true) {
                            participant.copy(
                                actor =
                                    participant.actor?.copy(
                                        id = FHIRString("$testTenant-$fakePractitionerId"),
                                        reference = FHIRString("Practitioner/$testTenant-$fakePractitionerId"),
                                    ),
                            )
                        } else {
                            participant
                        }
                    },
            )
        AidboxTestData.add(fakeAidboxAppointment)

        // mock: appointment-publish event
        MockOCIServerClient.createExpectations("Appointment", fakeAppointmentId, tenantInUse)
        MockOCIServerClient.createExpectations("Practitioner", fakePractitionerId, tenantInUse)

        val metadata =
            Metadata(
                runId = "123456",
                runDateTime = OffsetDateTime.now(),
            )
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxAppointment),
            metadata = metadata,
        )

        waitForMessage(1)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Practitioner"))

        // Now publish the same event
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxAppointment),
            metadata = metadata,
        )

        waitForMessage(2)
        val messageList2 = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList2)
        assertEquals(2, messageList2.size)
        assertEquals(1, getAidboxResourceCount("Practitioner"))

        // The message IDs are actually in reverse order, so grabbing the first
        val publishResponse =
            messageList2.first().destinationMessages.find { it.connectorName == "Publish Practitioners" }!!.response!!
        assertTrue(
            publishResponse.content.contains(
                "<message>All requested resources have already been processed this run: 123456:Practitioner:null:$testTenant:$fakePractitionerId</message>",
            ),
        )
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple appointments and practitioners`(testTenant: String) {
        tenantInUse = testTenant

        // mock: there are practitioners at the EHR

        val fakePractitioner1 = practitioner { }
        val fakePractitioner2 = practitioner { }
        val fakePractitioner1Id = MockEHRTestData.add(fakePractitioner1)
        val fakePractitioner2Id = MockEHRTestData.add(fakePractitioner2)

        // mock: there are appointments at the EHR that got published to Ronin

        val startDate = 2.daysFromNow()
        val endDate = 3.daysFromNow()
        val fakeAppointment1 =
            appointment {
                status of "pending"
                participant of
                    listOf(
                        participant {
                            status of "accepted"
                            actor of reference("Practitioner", fakePractitioner1Id)
                        },
                    )
                minutesDuration of 8
                start of startDate
                end of endDate
            }
        val fakeAppointment2 =
            appointment {
                status of "pending"
                participant of
                    listOf(
                        participant {
                            status of "accepted"
                            actor of reference("Practitioner", fakePractitioner2Id)
                        },
                    )
                minutesDuration of 8
                start of startDate
                end of endDate
            }
        val appointment1Id = MockEHRTestData.add(fakeAppointment1)
        val appointment2Id = MockEHRTestData.add(fakeAppointment2)

        val aidboxAppointment1Id = "$tenantInUse-$appointment1Id"
        val aidboxAppointment2Id = "$tenantInUse-$appointment2Id"
        val aidboxAppointment1 =
            fakeAppointment1.copy(
                id = Id(aidboxAppointment1Id),
                identifier = fakeAppointment1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(appointment1Id),
            )
        val aidboxAppointment2 =
            fakeAppointment2.copy(
                id = Id(aidboxAppointment2Id),
                identifier = fakeAppointment2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(appointment2Id),
            )
        AidboxTestData.add(aidboxAppointment1)
        AidboxTestData.add(aidboxAppointment2)

        MockOCIServerClient.createExpectations("Practitioner", fakePractitioner1Id, tenantInUse)
        MockOCIServerClient.createExpectations("Practitioner", fakePractitioner2Id, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(aidboxAppointment1, aidboxAppointment2),
        )
        waitForMessage(1)
        assertEquals(2, getAidboxResourceCount("Practitioner"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        // mock: practitioner at the EHR
        val fakePractitioner = practitioner { }
        val fakePractitionerId = MockEHRTestData.add(fakePractitioner)
        MockOCIServerClient.createExpectations("Practitioner", fakePractitionerId, testTenant)

        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakePractitionerId),
            resourceType = ResourceType.Practitioner,
        )
        waitForMessage(1)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("Practitioner"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = TEST_TENANT,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.Practitioner,
        )
        waitForMessage(1)
        assertEquals(0, getAidboxResourceCount("Practitioner"))
    }
}
