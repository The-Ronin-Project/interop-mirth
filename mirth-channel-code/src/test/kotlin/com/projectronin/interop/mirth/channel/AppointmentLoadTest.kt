package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppointmentLoadTest {

    private lateinit var channel: AppointmentLoad

    @BeforeEach
    fun setup() {
        channel = AppointmentLoad(mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("AppointmentLoad", channel.rootName)
        assertEquals("interop-mirth-appointment_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
