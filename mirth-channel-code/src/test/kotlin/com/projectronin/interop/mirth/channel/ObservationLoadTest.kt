package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObservationLoadTest {

    private lateinit var channel: ObservationLoad

    @BeforeEach
    fun setup() {
        channel = ObservationLoad(mockk(), mockk(), mockk())
    }

    @Test
    fun `create channel - works`() {
        assertEquals("ObservationLoad", channel.rootName)
        assertEquals("interop-mirth-observation_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
