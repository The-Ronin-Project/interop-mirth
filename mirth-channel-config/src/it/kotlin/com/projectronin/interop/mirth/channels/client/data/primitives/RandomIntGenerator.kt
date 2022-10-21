package com.projectronin.interop.mirth.channels.client.data.primitives

open class RandomIntGenerator(private val minimum: Int = 1, private val maximum: Int = 50) :
    RandomGenerator<Int>() {
    override fun generateInternal(): Int {
        return randomInt(minimum, maximum)
    }
}
