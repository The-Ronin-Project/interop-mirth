package com.projectronin.interop.mirth.channels

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.mirth.PATIENT_LOAD_CHANNEL_NAME
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PatientLoadTest : BaseChannelTest(
    PATIENT_LOAD_CHANNEL_NAME,
    listOf("Patient", "Condition", "Appointment", "Observation"),
    listOf("Patient", "Condition", "Appointment", "Observation"),
) {
    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `channel works with multiple patients`(testTenant: String) {
        tenantInUse = testTenant
        val patient1 =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 3
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000001"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // This is required to generate the Epic response.
                        },
                        name {
                            use of "official"
                        },
                    )
                gender of "male"
                telecom of emptyList()
            }
        val patient1Id = MockEHRTestData.add(patient1)
        val patient2 =
            patient {
                birthDate of
                    date {
                        year of 1990
                        month of 1
                        day of 4
                    }
                identifier of
                    listOf(
                        identifier {
                            system of "mockPatientInternalSystem"
                        },
                        identifier {
                            system of "mockEHRMRNSystem"
                            value of "1000000002"
                        },
                    )
                name of
                    listOf(
                        name {
                            use of "usual" // This is required to generate the Epic response.
                        },
                        name {
                            use of "official"
                        },
                    )
                gender of "female"
                telecom of emptyList()
            }
        val patient2Id = MockEHRTestData.add(patient2)
        MockOCIServerClient.createExpectations("patient", patient1Id, testTenant)
        MockOCIServerClient.createExpectations("patient", patient2Id, testTenant)

        // push event to get picked up
        KafkaClient.testingClient.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patient1Id, patient2Id),
            ResourceType.Patient,
        )

        waitForMessage(1)
        assertEquals(2, getAidboxResourceCount("Patient"))
    }
}
