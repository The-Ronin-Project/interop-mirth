package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.mirth.kafkaPatientQueueChannelName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

class KafkaPatientQueueTest : BaseChannelTest(kafkaPatientQueueChannelName, listOf("Patient"), listOf("Patient")) {
    private val patientType = "Patient"

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `patients can be queued`(testTenant: String) {
        tenantInUse = testTenant
        val mrn = Random.nextInt(10000, 99999).toString()
        val patient = patient {
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
                    value of mrn
                }
            )
            name of listOf(
                name {
                    use of "usual" // This is required to generate the Epic response.
                },
                name {
                    use of "official"
                }
            )
            gender of "male"
            telecom of emptyList()
        }
        val patientName = patient.name.first()
        val fhirId = MockEHRTestData.add(patient)

        MockOCIServerClient.createExpectations(patientType, fhirId, tenantInUse)

        // query for patient from 'EHR'
        ProxyClient.getPatientByNameAndDob(
            tenantInUse,
            patientName.family!!.value!!,
            patientName.given.first().value!!,
            "1990-01-03"
        )

        // make sure a message queued in mirth
        waitForMessage(1)
        assertEquals(1, getAidboxResourceCount("Patient"))
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPublishPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Patient::class)
        assertEquals(fhirId, datalakeFhirResource.findFhirId())
    }
}
