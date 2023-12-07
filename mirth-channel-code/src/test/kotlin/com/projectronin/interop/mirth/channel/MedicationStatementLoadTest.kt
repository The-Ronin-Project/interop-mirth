package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MedicationStatementLoadTest {
    private lateinit var channel: MedicationStatementLoad

    @BeforeEach
    fun setup() {
        channel = MedicationStatementLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("MedicationStatementLoad", channel.rootName)
        assertEquals("interop-mirth-medication-statement_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
