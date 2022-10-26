package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.externalIdentifier
import com.projectronin.interop.mirth.channels.client.data.resources.practitioner
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val practitionerQueueChannelName = "PractitionerQueue"

class PractitionerQueueTest : BaseMirthChannelTest(practitionerQueueChannelName, listOf("Practitioner")) {
    private val practitionerType = "Practitioner"
    @Test
    fun `queued practitioners are processed`() {
        val practitioner = practitioner {
            identifier generate 1 plus externalIdentifier {
                system of "mockEHRProviderSystem"
                value of "1234"
            }
        }
        val practitionerId = MockEHRTestData.add(practitioner)

        // Validate there are no current Practitioners.
        assertEquals(0, getAidboxResourceCount(practitionerType))

        // Queue up the practitioner
        val proxyNode = ProxyClient.getPractitionerByFHIRId(practitionerId, testTenant)
        assertEquals(
            "\"$testTenant-$practitionerId\"",
            proxyNode["data"]["getPractitionerById"]["id"].toString()
        )

        // start channel
        deployAndStartChannel(true)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        // practitioner successfully added to Aidbox
        assertEquals(1, getAidboxResourceCount(practitionerType))
    }

    @Test
    fun `no data no message`() {
        assertEquals(0, getAidboxResourceCount(practitionerType))
        // start channel
        deployAndStartChannel(false)
        // just wait a moment
        pause()
        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(0, list.size)

        // nothing added
        assertEquals(0, getAidboxResourceCount(practitionerType))
    }
}
