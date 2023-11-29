package com.projectronin.interop.mirth.util

import com.projectronin.interop.mirth.channel.util.splitDateRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DateUtilTest {

    @Test
    fun `date ranges are split`() {
        val start = OffsetDateTime.of(2023, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = start.plusDays(10)
        val pairs = splitDateRange(start, end, 2)
        assertEquals(6, pairs.size)
        assertEquals(start, pairs[0].first)
        assertEquals(end, pairs[5].second)
    }

    @Test
    fun `date ranges are not split when range is bigger`() {
        val start = OffsetDateTime.of(2023, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = start.plusDays(10)
        val pairs = splitDateRange(start, end, 12)
        assertEquals(1, pairs.size)
        assertEquals(start, pairs[0].first)
        assertEquals(end, pairs[0].second)
    }
}
