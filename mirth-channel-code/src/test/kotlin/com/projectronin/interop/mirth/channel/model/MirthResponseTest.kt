package com.projectronin.interop.mirth.channel.model

import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MirthResponseTest {
    @Test
    fun `full object can be created`() {
        val message =
            MirthResponse(
                message = "abc",
                dataMap = mapOf("def" to "ghi"),
                detailedMessage = "jkl",
                status = MirthResponseStatus.SENT,
            )
        assertEquals("abc", message.message)
        assertEquals("ghi", message.dataMap["def"])
        assertEquals("jkl", message.detailedMessage)
        assertEquals(MirthResponseStatus.SENT, message.status)
    }

    @Test
    fun `object can have empty dataMap`() {
        val message =
            MirthResponse(
                message = "abc",
                detailedMessage = "jkl",
                status = MirthResponseStatus.SENT,
            )
        assertEquals("abc", message.message)
        assertNull(message.dataMap["def"])
        assertEquals(emptyMap<String, Any>(), message.dataMap)
        assertEquals("jkl", message.detailedMessage)
        assertEquals(MirthResponseStatus.SENT, message.status)
    }

    @Test
    fun `minimal object can be created`() {
        val message = MirthResponse(MirthResponseStatus.SENT)
        assertEquals("", message.message)
        assertNull(message.dataMap["def"])
        assertEquals("", message.detailedMessage)
        assertEquals(MirthResponseStatus.SENT, message.status)
    }
}
