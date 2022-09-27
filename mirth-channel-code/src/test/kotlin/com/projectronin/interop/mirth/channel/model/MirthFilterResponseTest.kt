package com.projectronin.interop.mirth.channel.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MirthFilterResponseTest {
    @Test
    fun `object works`() {
        val test = MirthFilterResponse(true)
        assertTrue(test.result)
        assertTrue(test.dataMap.isEmpty())
    }
    @Test
    fun `object works more`() {
        val test = MirthFilterResponse(false, mapOf("wow" to "woah"))
        assertFalse(test.result)
        assertEquals("woah", test.dataMap["wow"])
    }
}
