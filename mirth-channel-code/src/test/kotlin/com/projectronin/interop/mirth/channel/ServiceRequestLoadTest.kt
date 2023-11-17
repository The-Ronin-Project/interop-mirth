package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServiceRequestLoadTest {

    private lateinit var channel: ServiceRequestLoad

    @BeforeEach
    fun setup() {
        channel = ServiceRequestLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("ServiceRequestLoad", channel.rootName)
        assertEquals("interop-mirth-service-request_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
