package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MedicationLoadTest {

    private lateinit var channel: MedicationLoad

    @BeforeEach
    fun setup() {
        channel = MedicationLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("MedicationLoad", channel.rootName)
        assertEquals("interop-mirth-medication_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
