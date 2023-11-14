package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiagnosticReportLoadTest {
    private lateinit var channel: DiagnosticReportLoad

    @BeforeEach
    fun setup() {
        channel = DiagnosticReportLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `create channel - works`() {
        assertEquals("DiagnosticReportLoad", channel.rootName)
        assertEquals("interop-mirth-diagnostic-report_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
