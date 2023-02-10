package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import com.projectronin.interop.mirth.channels.client.MockOCIServerClient
import com.projectronin.interop.mirth.channels.client.TenantClient
import com.projectronin.interop.mirth.channels.client.data.datatypes.reference
import com.projectronin.interop.mirth.channels.client.data.resources.location
import com.projectronin.interop.mirth.channels.client.data.resources.practitioner
import com.projectronin.interop.mirth.channels.client.data.resources.practitionerRole
import com.projectronin.interop.mirth.channels.client.mirth.MirthClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val practitionerLoadName = "PractitionerLoad"

class PractitionerLoadTest : BaseMirthChannelTest(
    practitionerLoadName,
    listOf("Practitioner", "PractitionerRole", "Location"),
    listOf("Practitioner", "PractitionerRole", "Location"),
) {
    private val practitionerType = "Practitioner"
    private val practitionerRoleType = "PractitionerRole"
    private val locationType = "Location"

    private var existingConfig: TenantClient.MirthConfig = TenantClient.getMirthConfig(testTenant)

    @AfterEach
    fun `restore existing mirth config`() {
        TenantClient.putMirthConfig(testTenant, existingConfig)
    }

    @Test
    fun `no location fails`() {
        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf("fail")))
        assertEquals(0, getAidboxResourceCount(practitionerType))
        assertEquals(0, getAidboxResourceCount(practitionerRoleType))
        assertEquals(0, getAidboxResourceCount(locationType))

        deployAndStartChannel(true)

        assertEquals(0, getAidboxResourceCount(practitionerType))
        assertEquals(0, getAidboxResourceCount(practitionerRoleType))
        assertEquals(0, getAidboxResourceCount(locationType))

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(1, messageList.size)
        messageList.forEach { ids ->
            val message = MirthClient.getMessageById(testChannelId, ids)
            assertEquals("ERROR", message.sourceStatus)
        }
    }
    @Test
    fun `can load resources`() {
        val locationResource = location { }
        val locationId = MockEHRTestData.add(locationResource)
        val practitionerResource = practitioner { }
        val practitionerId = MockEHRTestData.add(practitionerResource)

        val practitionerRoleResource = practitionerRole {
            practitioner of reference(practitionerType, practitionerId)
            location of listOf(reference(locationType, locationId))
        }
        val practitionerRoleId = MockEHRTestData.add(practitionerRoleResource)
        val expectedMap = mapOf(
            practitionerType to listOf(practitionerId),
            locationType to listOf(locationId),
            practitionerRoleType to listOf(practitionerRoleId)
        )
        MockOCIServerClient.createExpectations(expectedMap, testTenant)

        TenantClient.putMirthConfig(testTenant, TenantClient.MirthConfig(locationIds = listOf(locationId)))

        deployAndStartChannel(true)

        assertEquals(1, getAidboxResourceCount(practitionerType))
        assertEquals(1, getAidboxResourceCount(practitionerRoleType))
        assertEquals(1, getAidboxResourceCount(locationType))

        val messageList = MirthClient.getChannelMessageIds(testChannelId)
        assertEquals(3, messageList.size)

        assertAllConnectorsSent(messageList)

        // ensure data lake gets what it needs
        MockOCIServerClient.verify(3)
        val resources = MockOCIServerClient.getAllPutsAsResources()
        verifyAllPresent(resources, expectedMap)
    }
}
