package com.projectronin.interop.mirth.channels.client.data.primitives

import com.projectronin.interop.mirth.channels.client.data.Generator
import java.util.Locale
import kotlin.random.Random

abstract class RandomGenerator<T> : Generator<T>() {
    private val random = Random.Default

    protected fun randomString(minimumLength: Int, maximumLength: Int): String {
        val length = random.nextInt(minimumLength, maximumLength)
        val word = (1..length).map { 'a' + random.nextInt(0, 26) }.joinToString("")
        return word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    protected fun randomInt(minimum: Int, maximum: Int): Int {
        return random.nextInt(minimum, maximum)
    }
}
