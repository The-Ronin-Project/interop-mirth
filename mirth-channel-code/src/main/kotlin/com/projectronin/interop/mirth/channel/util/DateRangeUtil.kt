package com.projectronin.interop.mirth.channel.util

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Collections
import kotlin.math.min

// split a given set of dates into a list of pairs of roughly equal length
fun splitDateRange(
    startDate: OffsetDateTime,
    endDate: OffsetDateTime,
    maxDayRange: Int,
): List<Pair<OffsetDateTime, OffsetDateTime>> {
    // add one, since this function technically returns the number of days BETWEEN two days
    // don't need to add 2 tho, because the range function we use later has an inclusive end
    val daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1
    // don't step by more days than there are in the entire range
    val stepSize = min(daysBetween, maxDayRange.toLong())
    val dateRange = 0 until daysBetween step stepSize
    return dateRange.map {
        val sectionStart = startDate.plusDays(it)
        // don't step past the end date
        val sectionEnd = Collections.min(listOf(endDate, sectionStart.plusDays(stepSize)))
        Pair(sectionStart, sectionEnd)
    }
}
