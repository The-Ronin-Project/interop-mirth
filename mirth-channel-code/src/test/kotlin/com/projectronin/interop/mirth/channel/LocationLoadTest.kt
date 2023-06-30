package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocationLoadTest {

    private lateinit var channel: LocationLoad

    @BeforeEach
    fun setup() {
        channel = LocationLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("LocationLoad", channel.rootName)
        assertEquals("interop-mirth-location_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
