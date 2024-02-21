package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.serviceRequest
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.ronin.generators.resource.observation.rcdmObservation
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmAppointment
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmMedicationRequest
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmMedicationStatement
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.SERVICE_REQUEST_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ServiceRequestLoadTest : BaseChannelTest(
    SERVICE_REQUEST_LOAD_CHANNEL_NAME,
    listOf("ServiceRequest", "Appointment", "MedicationRequest", "MedicationStatement", "Observation"),
    listOf("ServiceRequest", "Appointment", "MedicationRequest", "MedicationStatement", "Observation"),
) {
    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple service requests and appointments`(testTenant: String) {
        tenantInUse = testTenant
        val fakeServiceRequest1 =
            serviceRequest {
                identifier of
                    listOf(
                        identifier {
                            system of "mockServiceRequestSystem"
                            value of "ServiceRequest/1"
                        },
                    )
                category of
                    listOf(
                        CodeableConcept(
                            coding =
                                listOf(
                                    Coding(
                                        system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCategory"),
                                        code = Code("1"),
                                    ),
                                ),
                        ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                subject of reference("Patient")
            }
        val serviceRequestFhirId1 = MockEHRTestData.add(fakeServiceRequest1)
        val fakeServiceRequest2 =
            serviceRequest {
                identifier of
                    listOf(
                        identifier {
                            system of "mockServiceRequestSystem"
                            value of "ServiceRequest/2"
                        },
                    )
                category of
                    listOf(
                        CodeableConcept(
                            coding =
                                listOf(
                                    Coding(
                                        system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCategory"),
                                        code = Code("1"),
                                    ),
                                ),
                        ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                subject of reference("Patient")
            }
        val serviceRequestFhirId2 = MockEHRTestData.add(fakeServiceRequest2)

        val fakeServiceRequest3 =
            serviceRequest {
                identifier of
                    listOf(
                        identifier {
                            system of "mockServiceRequestSystem"
                            value of "ServiceRequest/2"
                        },
                    )
                category of
                    listOf(
                        CodeableConcept(
                            coding =
                                listOf(
                                    Coding(
                                        system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCategory"),
                                        code = Code("1"),
                                    ),
                                ),
                        ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                subject of reference("Patient")
            }
        val serviceRequestFhirId3 = MockEHRTestData.add(fakeServiceRequest3)

        val fakeAppointment1 =
            rcdmAppointment(tenantInUse) {
                status of "arrived"
                participant of
                    listOf(
                        participant {
                            status of "accepted"
                            actor of reference("Location", "locationId1")
                        },
                    )
                basedOn of listOf(reference("ServiceRequest", serviceRequestFhirId1))
                start of 2.daysFromNow()
                end of 3.daysFromNow()
            }

        val fakeAppointment2 =
            rcdmAppointment(tenantInUse) {
                status of "arrived"
                participant of
                    listOf(
                        participant {
                            status of "accepted"
                            actor of reference("Location", "locationId2")
                        },
                    )
                basedOn of
                    listOf(
                        reference("ServiceRequest", serviceRequestFhirId2),
                        reference("ServiceRequest", serviceRequestFhirId2),
                    )
                start of 2.daysFromNow()
                end of 3.daysFromNow()
            }

        val fakeMedicationRequest =
            rcdmMedicationRequest(tenantInUse) {
                basedOn of listOf(reference("ServiceRequest", serviceRequestFhirId3))
            }

        val fakeMedicationStatement =
            rcdmMedicationStatement(tenantInUse) {
                basedOn of listOf(reference("ServiceRequest", serviceRequestFhirId1))
            }

        val fakeObservation =
            rcdmObservation(tenantInUse) {
                basedOn of listOf(reference("ServiceRequest", serviceRequestFhirId3))
            }

        val serviceRequest1ID = MockEHRTestData.add(fakeServiceRequest1)
        val serviceRequest2ID = MockEHRTestData.add(fakeServiceRequest1)
        val serviceRequest3ID = MockEHRTestData.add(fakeServiceRequest1)
        val serviceRequest4ID = MockEHRTestData.add(fakeServiceRequest1)
        val serviceRequest5ID = MockEHRTestData.add(fakeServiceRequest1)
        val serviceRequest6ID = MockEHRTestData.add(fakeServiceRequest2)
        val serviceRequest7ID = MockEHRTestData.add(fakeServiceRequest3)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequest1ID, tenantInUse)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequest2ID, tenantInUse)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequest3ID, tenantInUse)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequest4ID, tenantInUse)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequest5ID, tenantInUse)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequest6ID, tenantInUse)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequest7ID, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources =
                listOf(
                    fakeAppointment1,
                    fakeAppointment2,
                    fakeMedicationRequest,
                    fakeMedicationStatement,
                    fakeObservation,
                ),
        )

        waitForMessage(1)
        assertEquals(3, getAidboxResourceCount("ServiceRequest"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val fakeServiceRequest1 =
            serviceRequest {
                identifier of
                    listOf(
                        identifier {
                            system of "mockServiceRequestSystem"
                            value of "ServiceRequest/1"
                        },
                    )
                category of
                    listOf(
                        CodeableConcept(
                            coding =
                                listOf(
                                    Coding(
                                        system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCategory"),
                                        code = Code("1"),
                                    ),
                                ),
                        ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ServiceRequestCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                subject of reference("Patient")
            }
        val serviceRequestFhirId1 = MockEHRTestData.add(fakeServiceRequest1)
        val fakeAppointment1 =
            appointment {
                status of "arrived"
                participant of
                    listOf(
                        participant {
                            status of "accepted"
                            actor of reference("Location", "locationId1")
                        },
                    )
                basedOn of listOf(reference("ServiceRequest", serviceRequestFhirId1))
                start of 2.daysFromNow()
                end of 3.daysFromNow()
            }
        val appt1Id = MockEHRTestData.add(fakeAppointment1)
        val aidboxAppt1Id = "$tenantInUse-$appt1Id"
        val aidboxAppt2 =
            fakeAppointment1.copy(
                id = Id(aidboxAppt1Id),
                identifier = fakeAppointment1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(appt1Id),
            )
        AidboxTestData.add(aidboxAppt2)
        MockOCIServerClient.createExpectations("ServiceRequest", serviceRequestFhirId1, testTenant)
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(serviceRequestFhirId1),
            resourceType = ResourceType.ServiceRequest,
        )

        waitForMessage(1)
        assertEquals(1, getAidboxResourceCount("ServiceRequest"))
    }

    @Test
    fun `non-existent request errors`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = TEST_TENANT,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("nothing to see here"),
            resourceType = ResourceType.ServiceRequest,
        )

        waitForMessage(1)
        assertEquals(0, getAidboxResourceCount("ServiceRequest"))
    }
}
