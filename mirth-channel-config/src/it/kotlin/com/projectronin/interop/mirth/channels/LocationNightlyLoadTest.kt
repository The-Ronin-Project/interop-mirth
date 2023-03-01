package com.projectronin.interop.mirth.channels

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.generators.resources.location
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val locationNightlyLoadChannelName = "LocationNightlyLoad"

class LocationNightlyLoadTest : BaseMirthChannelTest(locationNightlyLoadChannelName, listOf("Location")) {
    private val locationType = "Location"

    @Test
    fun `fails when location isn't in ehr`() {
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf("Not present")))
        deployAndStartChannel(true)

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, messageList.size)

        messageList.forEach { ids ->
            val message = MirthClient.getMessageById(testChannelId, ids)
            message.destinationMessages.forEach {
                Assertions.assertTrue(it.status == "ERROR" || it.status == "FILTERED")
            }
        }
    }

    @Test
    fun `loads location`() {
        val locationResource = location { }
        val locationId = MockEHRTestData.add(locationResource)
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf(locationId)))

        MockOCIServerClient.createExpectations(locationType, locationId)
        assertEquals(0, getAidboxResourceCount(locationType))

        // start channel
        deployAndStartChannel(true)

        val list = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, list.size)
        assertAllConnectorsSent(list)

        // condition successfully added to Aidbox
        assertEquals(1, getAidboxResourceCount(locationType))

        // ensure data lake gets what it needs
        MockOCIServerClient.verify()
        val datalakeObject = MockOCIServerClient.getLastPutBody()
        val datalakeFhirResource = JacksonUtil.readJsonObject(datalakeObject, Location::class)
        assertEquals(locationId, datalakeFhirResource.getFhirIdentifier()?.value?.value)
    }
}
