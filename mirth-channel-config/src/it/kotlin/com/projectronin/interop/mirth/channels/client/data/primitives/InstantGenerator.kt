package com.projectronin.interop.mirth.channels.client.data.primitives

import com.projectronin.interop.fhir.r4.datatype.primitive.Instant
import com.projectronin.interop.mirth.channels.client.data.Generator
import java.time.LocalDate

class InstantGenerator : RandomGenerator<Instant>() {
    val year: Generator<Int> = RandomIntGenerator(1920, 2015)
    val month: Generator<Int> = RandomIntGenerator(1, 12)
    val day: Generator<Int> = RandomIntGenerator(1, 28) // To prevent invalid dates
    val hour: Generator<Int> = RandomIntGenerator(0, 23)
    val minute: Generator<Int> = RandomIntGenerator(0, 59)
    val second: Generator<Int> = RandomIntGenerator(0, 59)

    override fun generateInternal(): Instant =
        Instant(
            "%d-%02d-%02dT%02d:%02d:%02dZ".format(
                year.generate(),
                month.generate(),
                day.generate(),
                hour.generate(),
                minute.generate(),
                second.generate()
            )
        )
}

fun instant(block: InstantGenerator.() -> Unit): Instant {
    val instant = InstantGenerator()
    instant.apply(block)
    return instant.generate()!!
}

fun Int.daysAgo(): Instant {
    val adjustedNow = LocalDate.now().minusDays(this.toLong())
    return instant {
        year of adjustedNow.year
        month of adjustedNow.month.value
        day of adjustedNow.dayOfMonth
    }
}

fun Int.daysFromNow(): Instant {
    val adjustedNow = LocalDate.now().plusDays(this.toLong())
    return instant {
        year of adjustedNow.year
        month of adjustedNow.month.value
        day of adjustedNow.dayOfMonth
    }
}
