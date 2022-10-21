package com.projectronin.interop.mirth.channels

import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.data.datatypes.codeableConcept
import com.projectronin.interop.mirth.channels.client.data.datatypes.coding
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.resources.observation
import com.projectronin.interop.mirth.channels.client.data.resources.patient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val observationLoadChannelName = "ObservationLoad"

class ObservationLoadTest : BaseMirthChannelTest(observationLoadChannelName, listOf("Patient", "Observation"), listOf("Patient", "Observation")) {
    val patientType = "Patient"
    val observationType = "Observation"

    @Test
    fun `no patient errors`() {
        assertEquals(0, getAidboxResourceCount(patientType))
        assertEquals(0, getAidboxResourceCount(observationType))

        deployAndStartChannel(true)
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        list.forEach { ints ->
            val message = MirthClient.getMessageById(testChannelId, ints)
            message.destinationMessages.forEach {
                assertEquals("ERROR", it.status)
            }
        }

        // nothing added
        assertEquals(0, getAidboxResourceCount(patientType))
        assertEquals(0, getAidboxResourceCount(observationType))
    }
    @Test
    fun `no observations`() {
        val patient = patient {}
        MockEHRTestData.add(patient)

        val aidboxPatient = patient.copy(
            identifier = patient.identifier + tenantIdentifier(testTenant)
        )
        AidboxTestData.add(aidboxPatient)

        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(0, getAidboxResourceCount(observationType))

        deployAndStartChannel(false)
        pause()
        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(0, getAidboxResourceCount(observationType))
    }

    @Test
    fun `simple load`() {
        val patient = patient {}
        val patientId = MockEHRTestData.add(patient)
        val observation = observation {
            subject of reference(patientType, patientId)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri.value
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept { text of "blah" }
        }
        MockEHRTestData.add(observation)

        val aidboxPatient = patient.copy(
            id = Id("$testTenant-$patientId"),
            identifier = patient.identifier + tenantIdentifier(testTenant)
        )
        AidboxTestData.add(aidboxPatient)

        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(0, getAidboxResourceCount(observationType))

        deployAndStartChannel(true)
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(observationType))
    }

    @Test
    fun `two patients, two messages`() {
        val patient1 = patient {}
        val patient2 = patient {}

        val patient1Id = MockEHRTestData.add(patient1)
        val patient2Id = MockEHRTestData.add(patient2)
        val observation1 = observation {
            subject of reference(patientType, patient1Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri.value
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept { text of "blah" }
        }
        val observation2 = observation {
            subject of reference(patientType, patient2Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri.value
                            code of ObservationCategoryCodes.LABORATORY.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept { text of "blah" }
        }
        MockEHRTestData.add(observation1)
        MockEHRTestData.add(observation2)

        val aidboxPatient1 = patient1.copy(
            id = Id("$testTenant-$patient1Id"),
            identifier = patient1.identifier + tenantIdentifier(testTenant)
        )
        val aidboxPatient2 = patient2.copy(
            id = Id("$testTenant-$patient2Id"),
            identifier = patient2.identifier + tenantIdentifier(testTenant)

        )
        AidboxTestData.add(aidboxPatient1)
        AidboxTestData.add(aidboxPatient2)

        assertEquals(2, getAidboxResourceCount(patientType))
        assertEquals(0, getAidboxResourceCount(observationType))

        deployAndStartChannel(true)
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(2, list.size)
        assertEquals(2, getAidboxResourceCount(patientType))
        assertEquals(2, getAidboxResourceCount(observationType))
    }
}
