package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CarePlanLoadTest {
    private lateinit var channel: CarePlanLoad

    @BeforeEach
    fun setup() {
        channel = CarePlanLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("CarePlanLoad", channel.rootName)
        assertEquals("interop-mirth-care_plan_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
        assertEquals(30, channel.maxBackfillDays)
    }
}
