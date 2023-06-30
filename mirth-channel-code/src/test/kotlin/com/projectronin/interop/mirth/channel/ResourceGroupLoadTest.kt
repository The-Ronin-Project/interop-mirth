package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResourceGroupLoadTest {
    lateinit var channel: RequestGroupLoad

    @BeforeEach
    fun setup() {
        channel = RequestGroupLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        Assertions.assertEquals("RequestGroupLoad", channel.rootName)
        Assertions.assertEquals("interop-mirth-request_group_group", channel.channelGroupId)
        Assertions.assertEquals(1, channel.destinations.size)
    }
}
