package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channels.client.KafkaWrapper
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.primitives.date
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val patientLoadChannelName = "PatientLoad"

class PatientLoadTest : BaseMirthChannelTest(
    patientLoadChannelName,
    listOf("Patient"),
    listOf("Patient"),
    listOf(ResourceType.PATIENT)
) {

    @Test
    fun `channel works`() {

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
                }
            )
            gender of "male"
        }
        val patient1Id = MockEHRTestData.add(patient1)
        MockOCIServerClient.createExpectations("patient", patient1Id)
        // push event to get picked up
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patient1Id),
            ResourceType.PATIENT
        )
        deployAndStartChannel(true)

        // check that publish event was triggered
        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY)
        assertEquals(1, events.size)
    }

    @Test
    fun `channel works with multiple patients`() {

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
                }
            )
            gender of "male"
        }
        val patient1Id = MockEHRTestData.add(patient1)
        val patient2 = patient {
            birthDate of date {
                year of 1990
                month of 1
                day of 4
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
                    use of "usual" // This is required to generate the Epic response.
                }
            )
            gender of "female"
        }
        val patient2Id = MockEHRTestData.add(patient2)
        MockOCIServerClient.createExpectations("patient", patient1Id)
        MockOCIServerClient.createExpectations("patient", patient2Id)

        // push event to get picked up
        KafkaWrapper.kafkaLoadService.pushLoadEvent(
            testTenant,
            DataTrigger.NIGHTLY,
            listOf(patient1Id, patient2Id),
            ResourceType.PATIENT
        )
        deployAndStartChannel(true)

        // check that publish event was triggered
        val events = KafkaWrapper.kafkaPublishService.retrievePublishEvents(ResourceType.PATIENT, DataTrigger.NIGHTLY)
        assertEquals(2, events.size)
    }
}
