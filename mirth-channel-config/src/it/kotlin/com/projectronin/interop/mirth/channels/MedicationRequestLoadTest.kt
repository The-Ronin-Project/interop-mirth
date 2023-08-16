package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.medication
import com.projectronin.interop.fhir.generators.resources.medicationRequest
import com.projectronin.interop.fhir.generators.resources.patient
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

const val medicationRequestLoadChannelName = "MedicationRequestLoad"

class MedicationRequestLoadTest : BaseChannelTest(
    medicationRequestLoadChannelName,
    listOf("Patient", "MedicationRequest", "Medication"),
    listOf("Patient", "MedicationRequest", "Medication")
) {
    val patientType = "Patient"
    val medicationRequestType = "MedicationRequest"

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `check if channel works`(testTenant: String) {
        tenantInUse = testTenant
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

        val medicationRequest = medicationRequest {
            subject of reference(patientType, fakePatientId)
            requester of reference("Practitioner", "ffff")
            intent of "order"
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }
        val medicationRequestId = MockEHRTestData.add(medicationRequest)
        MockOCIServerClient.createExpectations(medicationRequestType, medicationRequestId)
        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.NIGHTLY,
            resources = listOf(fakeAidboxPatient)
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount(medicationRequestType))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and medication requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakePatient1 = patient {}
        val patient1Id = MockEHRTestData.add(fakePatient1)

        val fakePatient2 = patient {}
        val patient2Id = MockEHRTestData.add(fakePatient2)

        val roninPatient1 = fakePatient1.copy(
            id = Id("$tenantInUse-$patient1Id"),
            identifier = fakePatient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient1Id)
        )
        val roninPatient2 = fakePatient2.copy(
            id = Id("$tenantInUse-$patient2Id"),
            identifier = fakePatient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(patient2Id)
        )

        val fakeMedicationRequest1 = medicationRequest {
            subject of reference(patientType, patient1Id)
            requester of reference("Practitioner", "ffff")
            intent of "order"
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }

        val fakeMedicationRequest2 = medicationRequest {
            subject of reference(patientType, patient2Id)
            requester of reference("Practitioner", "ffff")
            intent of "order"
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }

        val medRequest1ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest2ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest3ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest4ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest5ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest6ID = MockEHRTestData.add(fakeMedicationRequest1)
        val medRequest7ID = MockEHRTestData.add(fakeMedicationRequest2)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest1ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest2ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest3ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest4ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest5ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest6ID, tenantInUse)
        MockOCIServerClient.createExpectations("MedicationRequest", medRequest7ID, tenantInUse)
        MockEHRTestData.validateAll()

        KafkaClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2)
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(7, getAidboxResourceCount("MedicationRequest"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant

        val fakePatient1 = patient {}
        val patient1Id = MockEHRTestData.add(fakePatient1)

        val fakeMedicationRequest1 = medicationRequest {
            subject of reference(patientType, patient1Id)
            requester of reference("Practitioner", "ffff")
            intent of "order"
            status of "active"
            medication of DynamicValues.reference(reference("Medication", "1234"))
        }
        val fakeMedicationRequestId = MockEHRTestData.add(fakeMedicationRequest1)
        MockOCIServerClient.createExpectations("MedicationRequest", fakeMedicationRequestId, testTenant)

        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(fakeMedicationRequestId),
            resourceType = ResourceType.MedicationRequest
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsSent(messageList)
        assertEquals(1, messageList.size)
        assertEquals(1, getAidboxResourceCount("MedicationRequest"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("doesn't exists"),
            resourceType = ResourceType.MedicationRequest
        )

        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsError(messageList)
        assertEquals(1, messageList.size)
        assertEquals(0, getAidboxResourceCount("MedicationRequest"))
    }
}
