package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.identifier
import com.projectronin.interop.mirth.channels.client.data.datatypes.name
import com.projectronin.interop.mirth.channels.client.data.resources.practitioner
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.random.Random

const val kafkaPractitionerQueueChannelName = "KafkaPractitionerQueue"

@Disabled // until we stop using db queues
class KafkaPractitionerQueueTest : BaseMirthChannelTest(kafkaPractitionerQueueChannelName, listOf("Practitioner")) {
    val practitionerType = "Practitioner"

    @Test
    fun `no data no message`() {
        assertEquals(0, getAidboxResourceCount(practitionerType))
        // start channel
        deployAndStartChannel(false)
        // make sure a message queued in mirth
        waitForMessage(1)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(0, list.size)

        // nothing added
        assertEquals(0, getAidboxResourceCount(practitionerType))
    }

    @Test
    fun `practitioners can be queued`() {
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

        MockOCIServerClient.createExpectations(practitionerType, fhirId)
        assertEquals(0, getAidboxResourceCount(practitionerType))

        // query for practitioner from 'EHR'
        ProxyClient.getPractitionerByFHIRId(fhirId, testTenant)

        // start channel
        deployAndStartChannel(true)
        // make sure a message queued in mirth
        waitForMessage(1)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)

        assertEquals(1, getAidboxResourceCount("Practitioner"))
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Practitioner::class)
        assertEquals(fhirId, datalakeFhirResource.getFhirIdentifier()?.value?.value)
    }
}
