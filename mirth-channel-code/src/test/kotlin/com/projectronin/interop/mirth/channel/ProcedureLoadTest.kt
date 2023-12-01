package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcedureLoadTest {

    private lateinit var channel: ProcedureLoad

    @BeforeEach
    fun setup() {
        channel = ProcedureLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("ProcedureLoad", channel.rootName)
        assertEquals("interop-mirth-procedure_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
