package com.projectronin.interop.mirth.channels.client.data.primitives

import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.mirth.channels.client.data.Generator

class DateGenerator : RandomGenerator<Date>() {
    val year: Generator<Int> = RandomIntGenerator(1920, 2015)
    val month: Generator<Int> = RandomIntGenerator(1, 12)
    val day: Generator<Int> = RandomIntGenerator(1, 28) // To prevent invalid dates

    override fun generateInternal(): Date =
        Date("%d-%02d-%02d".format(year.generate(), month.generate(), day.generate()))
}

fun date(block: DateGenerator.() -> Unit): Date {
    val date = DateGenerator()
    date.apply(block)
    return date.generate()!!
}
