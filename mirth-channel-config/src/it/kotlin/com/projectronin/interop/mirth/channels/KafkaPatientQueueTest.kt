package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.primitives.date
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

const val kafkaPatientQueueChannelName = "KafkaPatientQueue"

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
                }
            )
            gender of "male"
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

        // start channel
        deployAndStartChannel(true)
        // make sure a message queued in mirth
        waitForMessage(1)

        val list = MirthClient.getChannelMessageIds(testChannelId)

        assertEquals(1, list.size)
        assertEquals(1, getAidboxResourceCount("Patient"))
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Patient::class)
        assertEquals(fhirId, datalakeFhirResource.getFhirIdentifier()?.value?.value)
    }
}
