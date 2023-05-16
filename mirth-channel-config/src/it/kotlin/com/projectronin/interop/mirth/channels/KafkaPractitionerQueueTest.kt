package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.practitioner
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.ProxyClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

const val kafkaPractitionerQueueChannelName = "KafkaPractitionerQueue"

class KafkaPractitionerQueueTest : BaseChannelTest(kafkaPractitionerQueueChannelName, listOf("Practitioner")) {
    val practitionerType = "Practitioner"

    @ParameterizedTest
    @MethodSource("tenantsToTest")
    fun `practitioners can be queued`(testTenant: String) {
        tenantInUse = testTenant
        val mrn = Random.nextInt(10000, 99999).toString()
        val practitioner = practitioner {
            identifier of listOf(
                identifier {
                    system of "mockPractitionerInternalSystem"
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
        }
        val fhirId = MockEHRTestData.add(practitioner)

        MockOCIServerClient.createExpectations(practitionerType, fhirId, testTenant)
        assertEquals(0, getAidboxResourceCount(practitionerType))

        // query for practitioner from 'EHR'
        ProxyClient.getPractitionerByFHIRId(fhirId, testTenant)
        waitForMessage(1)

        assertEquals(1, getAidboxResourceCount("Practitioner"))
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPublishPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Practitioner::class)
        assertEquals(fhirId, datalakeFhirResource.getFhirIdentifier()?.value?.value)
    }
}
