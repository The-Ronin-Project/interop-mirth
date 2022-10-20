package com.projectronin.interop.mirth.channels.client.data.primitives

open class RandomStringGenerator(private val minimumLength: Int = 1, private val maximumLength: Int = 50) :
    RandomGenerator<String>() {
    override fun generateInternal(): String {
        return randomString(minimumLength, maximumLength)
    }
}
