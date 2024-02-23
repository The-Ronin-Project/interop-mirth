package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.dateTime
import com.projectronin.interop.fhir.generators.primitives.daysFromNow
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.procedure
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.ronin.generators.resource.observation.rcdmObservation
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmAppointment
import com.projectronin.interop.fhir.ronin.generators.resource.rcdmMedicationStatement
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.PROCEDURE_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.time.OffsetDateTime

class ProcedureLoadTest : BaseChannelTest(
    PROCEDURE_LOAD_CHANNEL_NAME,
    listOf("Appointment", "MedicationStatement", "Observation", "Procedure"),
    listOf("Appointment", "MedicationStatement", "Observation", "Procedure"),
) {
    val metadata1 =
        Metadata(
            runId = "123456",
            runDateTime = OffsetDateTime.now(),
            targetedResources = emptyList(),
        )

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple procedures and appointments`(testTenant: String) {
        tenantInUse = testTenant
        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val fakeProcedure1 =
            procedure {
                identifier of
                    listOf(
                        identifier {
                            system of "mockProcedureSystem"
                            value of "Procedure/1"
                        },
                    )
                category of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCategory"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                subject of reference("Patient")
                status of Code("not-done")
                performed of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
            }
        val procedureFhirId1 = MockEHRTestData.add(fakeProcedure1)
        val fakeProcedure2 =
            procedure {
                identifier of
                    listOf(
                        identifier {
                            system of "mockProcedureSystem"
                            value of "Procedure/2"
                        },
                    )
                category of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCategory"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                subject of reference("Patient")
                status of Code("not-done")
                performed of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
            }
        val procedureFhirId2 = MockEHRTestData.add(fakeProcedure2)

        val fakeProcedure3 =
            procedure {
                identifier of
                    listOf(
                        identifier {
                            system of "mockServiceRequestSystem"
                            value of "Procedure/3"
                        },
                    )
                category of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCategory"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                subject of reference("Patient")
                status of Code("not-done")
                performed of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
            }
        val procedureFhirId3 = MockEHRTestData.add(fakeProcedure3)

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
                reasonReference of listOf(reference("Procedure", procedureFhirId1))
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
                reasonReference of
                    listOf(
                        reference("Procedure", procedureFhirId2),
                        reference("Procedure", procedureFhirId2),
                    )
                start of 2.daysFromNow()
                end of 3.daysFromNow()
            }

        val fakeMedicationStatement =
            rcdmMedicationStatement(tenantInUse) {
                partOf of listOf(reference("Procedure", procedureFhirId1))
            }

        val fakeObservation =
            rcdmObservation(tenantInUse) {
                partOf of listOf(reference("Procedure", procedureFhirId3))
            }

        val procedure1ID = MockEHRTestData.add(fakeProcedure1)
        val procedure2ID = MockEHRTestData.add(fakeProcedure1)
        val procedure3ID = MockEHRTestData.add(fakeProcedure1)
        val procedure4ID = MockEHRTestData.add(fakeProcedure1)
        val procedure5ID = MockEHRTestData.add(fakeProcedure1)
        val procedure6ID = MockEHRTestData.add(fakeProcedure2)
        val procedure7ID = MockEHRTestData.add(fakeProcedure3)
        MockOCIServerClient.createExpectations("Procedure", procedure1ID, tenantInUse)
        MockOCIServerClient.createExpectations("Procedure", procedure2ID, tenantInUse)
        MockOCIServerClient.createExpectations("Procedure", procedure3ID, tenantInUse)
        MockOCIServerClient.createExpectations("Procedure", procedure4ID, tenantInUse)
        MockOCIServerClient.createExpectations("Procedure", procedure5ID, tenantInUse)
        MockOCIServerClient.createExpectations("Procedure", procedure6ID, tenantInUse)
        MockOCIServerClient.createExpectations("Procedure", procedure7ID, tenantInUse)

        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(fakeAppointment1, fakeAppointment2, fakeMedicationStatement, fakeObservation),
            metadata = metadata1,
        )

        waitForMessage(1)
        assertEquals(3, getAidboxResourceCount("Procedure"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val twoDaysAgo = LocalDateTime.now().minusDays(2)
        val fakeProcedure1 =
            procedure {
                identifier of
                    listOf(
                        identifier {
                            system of "mockProcedureSystem"
                            value of "Procedure/1"
                        },
                    )
                category of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCategory"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                code of
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri("http://projectronin.io/fhir/CodeSystem/ProcedureCode"),
                                    code = Code("1"),
                                ),
                            ),
                    )
                status of Code("not-done")
                subject of reference("Patient")
                performed of
                    DynamicValues.dateTime(
                        dateTime {
                            year of twoDaysAgo.year
                            month of twoDaysAgo.month.value
                            day of twoDaysAgo.dayOfMonth
                        },
                    )
            }
        val procedureFhirId1 = MockEHRTestData.add(fakeProcedure1)
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
                reasonReference of listOf(reference("Procedure", procedureFhirId1))
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
        MockOCIServerClient.createExpectations("Procedure", procedureFhirId1, testTenant)
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(procedureFhirId1),
            resourceType = ResourceType.Procedure,
            metadata = metadata1,
        )

        waitForMessage(1)
        assertEquals(1, getAidboxResourceCount("Procedure"))
    }
}
