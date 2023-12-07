package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MedicationAdministrationLoadTest {
    private lateinit var channel: MedicationAdministrationLoad

    @BeforeEach
    fun setup() {
        channel = MedicationAdministrationLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("MedicationAdministrationLoad", channel.rootName)
        assertEquals("interop-mirth-medication-administration_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
        assertEquals(
            listOf(ResourceType.Patient, ResourceType.MedicationRequest),
            channel.publishedResourcesSubscriptions,
        )
    }
}
