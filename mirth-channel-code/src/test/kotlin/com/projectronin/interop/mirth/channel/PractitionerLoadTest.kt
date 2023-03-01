package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PractitionerLoadTest {
    lateinit var channel: PractitionerLoad

    @BeforeEach
    fun setup() {
        channel = PractitionerLoad(mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("PractitionerLoad", channel.rootName)
        assertEquals("interop-mirth-practitioner", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
