package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.date
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.random.Random

const val patientQueueChannelName = "PatientQueue"

@Disabled
class PatientQueueTest : BaseMirthChannelTest(patientQueueChannelName, listOf("Patient")) {
    val patientType = "Patient"

    @Test
    fun `no data no message`() {
        assertEquals(0, getAidboxResourceCount(patientType))
        // start channel
        deployAndStartChannel(false)
        // just wait a moment
        pause()
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(0, list.size)

        // nothing added
        assertEquals(0, getAidboxResourceCount(patientType))
    }

    @Test
    fun `patients can be queued`() {
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

        MockOCIServerClient.createExpectations(patientType, fhirId)
        assertEquals(0, getAidboxResourceCount(patientType))

        // query for patient from 'EHR'
        ProxyClient.getPatientByNameAndDob(
            testTenant,
            patientName.family?.value!!,
            patientName.given.first().value!!,
            "1990-01-03"
        )

        // start channel
        deployAndStartChannel(true)
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)

        assertEquals(1, getAidboxResourceCount("Patient"))

        // datalake received the object
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Patient::class)
        assertEquals(fhirId, datalakeFhirResource.getFhirIdentifier()?.value?.value)
    }
}
