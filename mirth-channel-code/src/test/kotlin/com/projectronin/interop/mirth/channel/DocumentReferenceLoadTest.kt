package com.projectronin.interop.mirth.channel

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DocumentReferenceLoadTest {
    private lateinit var channel: DocumentReferenceLoad

    @BeforeEach
    fun setup() {
        channel = DocumentReferenceLoad(mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `create channel - works`() {
        assertEquals("DocumentReferenceLoad", channel.rootName)
        assertEquals("interop-mirth-document_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
