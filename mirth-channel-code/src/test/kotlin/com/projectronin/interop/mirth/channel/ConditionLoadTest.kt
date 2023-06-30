package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConditionLoadTest {

    private lateinit var channel: ConditionLoad

    @BeforeEach
    fun setup() {
        channel = ConditionLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `create channel - works`() {
        assertEquals("ConditionLoad", channel.rootName)
        assertEquals("interop-mirth-condition_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
