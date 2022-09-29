package com.projectronin.interop.mirth.channel.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MirthMessageTest {
    @Test
    fun `full object can be created`() {
        val message = MirthMessage(message = "abc", dataMap = mapOf("def" to "ghi"))
        assertEquals("abc", message.message)
        assertEquals("ghi", message.dataMap["def"])
    }

    @Test
    fun `object can have empty dataMap`() {
        val message = MirthMessage(message = "abc")
        assertEquals("abc", message.message)
        assertNull(message.dataMap["def"])
        assertEquals(emptyMap<String, Any>(), message.dataMap)
    }

    @Test
    fun `empty object can be created - call emptyMirthMessage`() {
        val message = emptyMirthMessage()
        assertEquals("", message.message)
        assertNull(message.dataMap["def"])
        assertEquals(emptyMap<String, Any>(), message.dataMap)
    }

    @Test
    fun `empty object can be created - manually`() {
        val message = MirthMessage()
        assertEquals("", message.message)
        assertNull(message.dataMap["def"])
        assertEquals(emptyMap<String, Any>(), message.dataMap)
    }

    @Test
    fun `object can have empty message`() {
        val message = MirthMessage(dataMap = mapOf("def" to "ghi"))
        assertEquals("", message.message)
        assertEquals("ghi", message.dataMap["def"])
    }
}
