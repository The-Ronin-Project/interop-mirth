package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PatientLoadTest {
    lateinit var channel: PatientLoad

    @BeforeEach
    fun setup() {
        channel = PatientLoad(mockk(), mockk(), mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("PatientLoad", channel.rootName)
        assertEquals(1, channel.destinations.size)
    }
}
