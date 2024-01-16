package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.diagnosticReport
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.DIAGNOSTIC_REPORT_LOAD_CHANNEL_NAME
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class DiagnosticReportLoadTest : BaseChannelTest(
    DIAGNOSTIC_REPORT_LOAD_CHANNEL_NAME,
    listOf("Patient", "DiagnosticReport"),
    listOf("Patient", "DiagnosticReport"),
) {
    private val patientType = "Patient"

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients and diagnostic reports`(testTenant: String) {
        tenantInUse = testTenant

        // MOCK PATIENTS AT THE EHR
        val fakePatient1 = patient {}
        val fakePatient2 = patient {}
        val fakePatient1Id = MockEHRTestData.add(fakePatient1)
        val fakePatient2Id = MockEHRTestData.add(fakePatient2)

        val roninPatient1 =
            fakePatient1.copy(
                id = Id("$tenantInUse-$fakePatient1Id"),
                identifier = fakePatient1.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatient1Id),
            )
        val roninPatient2 =
            fakePatient2.copy(
                id = Id("$tenantInUse-$fakePatient2Id"),
                identifier = fakePatient2.identifier + tenantIdentifier(tenantInUse) + fhirIdentifier(fakePatient2Id),
            )

        // MOCK: DX Reports at the EHR
        val fakeDiagnosticReport1 =
            diagnosticReport {
                subject of reference(patientType, fakePatient1Id)
                status of "registered"
                code of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://loinc.org"
                                    code of "58410-2"
                                    display of "Complete blood count (hemogram) panel - Blood by Automated count"
                                },
                            )
                        text of "Complete Blood Count"
                    }
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://terminology.hl7.org/CodeSystem/v2-0074"
                                        code of "LAB"
                                    },
                                )
                        },
                    )
            }

        val fakeDiagnosticReport2 =
            diagnosticReport {
                subject of reference(patientType, fakePatient2Id)
                status of "registered"
                code of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://loinc.org"
                                    code of "58410-2"
                                    display of "Complete blood count (hemogram) panel - Blood by Automated count"
                                },
                            )
                        text of "Complete Blood Count"
                    }
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://terminology.hl7.org/CodeSystem/v2-0074"
                                        code of "LAB"
                                    },
                                )
                        },
                    )
            }
        val diagnosticReport1Id = MockEHRTestData.add(fakeDiagnosticReport1)
        val diagnosticReport2Id = MockEHRTestData.add(fakeDiagnosticReport1)
        val diagnosticReport3Id = MockEHRTestData.add(fakeDiagnosticReport1)
        val diagnosticReport4Id = MockEHRTestData.add(fakeDiagnosticReport1)
        val diagnosticReport5Id = MockEHRTestData.add(fakeDiagnosticReport1)
        val diagnosticReport6Id = MockEHRTestData.add(fakeDiagnosticReport2)

        MockOCIServerClient.createExpectations("DiagnosticReport", diagnosticReport1Id, tenantInUse)
        MockOCIServerClient.createExpectations("DiagnosticReport", diagnosticReport2Id, tenantInUse)
        MockOCIServerClient.createExpectations("DiagnosticReport", diagnosticReport3Id, tenantInUse)
        MockOCIServerClient.createExpectations("DiagnosticReport", diagnosticReport4Id, tenantInUse)
        MockOCIServerClient.createExpectations("DiagnosticReport", diagnosticReport5Id, tenantInUse)
        MockOCIServerClient.createExpectations("DiagnosticReport", diagnosticReport6Id, tenantInUse)

        // MOCK PATIENT PUBLISH EVENT
        KafkaClient.testingClient.pushPublishEvent(
            tenantId = tenantInUse,
            trigger = DataTrigger.AD_HOC,
            resources = listOf(roninPatient1, roninPatient2),
        )
        waitForMessage(1)
        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertAllConnectorsStatus(messageList)
        assertEquals(1, messageList.size)
        assertEquals(6, getAidboxResourceCount("DiagnosticReport"))
    }

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works for ad-hoc requests`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 = patient { }
        val patient1Id = MockEHRTestData.add(patient1)
        val diagnosticReport1 =
            diagnosticReport {
                status of "registered"
                code of
                    codeableConcept {
                        coding of
                            listOf(
                                coding {
                                    system of "http://loinc.org"
                                    code of "58410-2"
                                    display of "Complete blood count (hemogram) panel - Blood by Automated count"
                                },
                            )
                        text of "Complete Blood Count"
                    }
                subject of reference("Patient", patient1Id)
                category of
                    listOf(
                        codeableConcept {
                            coding of
                                listOf(
                                    coding {
                                        system of "http://terminology.hl7.org/CodeSystem/v2-0074"
                                        code of "LAB"
                                    },
                                )
                        },
                    )
            }
        val diagnosticReportId = MockEHRTestData.add(diagnosticReport1)
        MockOCIServerClient.createExpectations("DiagnosticReport", diagnosticReportId, testTenant)
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = testTenant,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf(diagnosticReportId),
            resourceType = ResourceType.DiagnosticReport,
        )
        waitForMessage(1)
        assertEquals(1, getAidboxResourceCount("DiagnosticReport"))
    }

    @Test
    fun `no request errors`() {
        KafkaClient.testingClient.pushLoadEvent(
            tenantId = TEST_TENANT,
            trigger = DataTrigger.AD_HOC,
            resourceFHIRIds = listOf("nothing to see here"),
            resourceType = ResourceType.DiagnosticReport,
        )
        waitForMessage(1)
        assertEquals(0, getAidboxResourceCount("DiagnosticReport"))
    }
}
