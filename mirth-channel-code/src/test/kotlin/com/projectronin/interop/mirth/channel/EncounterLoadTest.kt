package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EncounterLoadTest {
    private lateinit var channel: EncounterLoad

    @BeforeEach
    fun setup() {
        channel = EncounterLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `create channel - works`() {
        Assertions.assertEquals("EncounterLoad", channel.rootName)
        Assertions.assertEquals("interop-mirth-encounter_group", channel.channelGroupId)
        Assertions.assertEquals(1, channel.destinations.size)
    }
}
