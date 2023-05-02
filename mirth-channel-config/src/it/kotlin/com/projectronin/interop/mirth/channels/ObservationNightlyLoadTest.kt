package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.generators.datatypes.codeableConcept
import com.projectronin.interop.fhir.generators.datatypes.coding
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.resources.observation
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.valueset.ObservationCategoryCodes
import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.fhirIdentifier
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import com.projectronin.interop.mirth.channels.client.tenantIdentifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val observationNightlyLoadChannelName = "ObservationNightlyLoad"

class ObservationNightlyLoadTest : BaseMirthChannelTest(observationNightlyLoadChannelName, listOf("Patient", "Observation"), listOf("Patient", "Observation")) {
    val patientType = "Patient"
    val observationType = "Observation"
    private val nowDate = DateTime(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

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
        val patientId = MockEHRTestData.add(patient)

        val aidboxPatient = patient.copy(
            identifier = patient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patientId)
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
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            effective of nowDate
        }
        val obsvId = MockEHRTestData.add(observation)

        MockOCIServerClient.createExpectations(observationType, obsvId)

        val aidboxPatient = patient.copy(
            id = Id("$testTenant-$patientId"),
            identifier = patient.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patientId)
        )
        AidboxTestData.add(aidboxPatient)

        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(0, getAidboxResourceCount(observationType))

        deployAndStartChannel(true)
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        assertEquals(1, getAidboxResourceCount(patientType))
        assertEquals(1, getAidboxResourceCount(observationType))

        // ensure data lake gets what it needs
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Observation::class)
        assertEquals(obsvId, datalakeFhirResource.getFhirIdentifier()?.value?.value)
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
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Weight"
                        code of Code("29463-7")
                    }
                )
            }
            effective of nowDate
        }
        val observation2 = observation {
            subject of reference(patientType, patient2Id)
            category of listOf(
                codeableConcept {
                    coding of listOf(
                        coding {
                            system of CodeSystem.OBSERVATION_CATEGORY.uri
                            code of ObservationCategoryCodes.VITAL_SIGNS.code
                        }
                    )
                }
            )
            status of "final"
            code of codeableConcept {
                coding of listOf(
                    coding {
                        system of CodeSystem.LOINC.uri
                        display of "Body Height"
                        code of Code("8302-2")
                    }
                )
            }
            effective of nowDate
        }
        val obsv1Id = MockEHRTestData.add(observation1)
        val obsv2Id = MockEHRTestData.add(observation2)
        val expectedMap = mapOf(
            observationType to listOf(obsv1Id, obsv2Id)
        )
        MockOCIServerClient.createExpectations(expectedMap)

        val aidboxPatient1 = patient1.copy(
            id = Id("$testTenant-$patient1Id"),
            identifier = patient1.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patient1Id)
        )
        val aidboxPatient2 = patient2.copy(
            id = Id("$testTenant-$patient2Id"),
            identifier = patient2.identifier + tenantIdentifier(testTenant) + fhirIdentifier(patient2Id)

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

        // ensure data lake gets what it needs
        MockOCIServerClient.verify(2)
        val resources = MockOCIServerClient.getAllPutsAsResources()
        verifyAllPresent(resources, expectedMap)
    }
}
