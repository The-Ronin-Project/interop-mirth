package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MedicationRequestLoadTest {
    private lateinit var channel: MedicationRequestLoad

    @BeforeEach
    fun setup() {
        channel = MedicationRequestLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("MedicationRequestLoad", channel.rootName)
        assertEquals("interop-mirth-medication-request_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
