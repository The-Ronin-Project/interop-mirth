package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.MirthClient
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.ProxyClient
import com.projectronin.interop.mirth.channels.client.externalIdentifier
import com.projectronin.interop.mirth.channels.client.practitioner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val practitionerQueueChannelName = "PractitionerQueue"

class PractitionerQueueTest : BaseMirthChannelTest(practitionerQueueChannelName, listOf("Practitioner")) {
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
        assertEquals(0, getAidboxResourceCount("Practitioner"))

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
        assertEquals(1, getAidboxResourceCount("Practitioner"))
    }
}
