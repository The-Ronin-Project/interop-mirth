package com.projectronin.interop.mirth.channels.client.data.primitives

import com.projectronin.interop.mirth.channels.client.data.Generator

open class ListGenerator<T>(private var count: Int, private val baseGenerator: Generator<T>) {
    private var staticValue: List<T>? = null
    private var additionalValues: MutableList<T> = mutableListOf()

    fun generate(): List<T> {
        return staticValue ?: ((1..count).mapNotNull { baseGenerator.generate() } + additionalValues)
    }

    infix fun of(value: List<T>) {
        staticValue = value
    }

    infix fun generate(count: Int): ListGenerator<T> {
        this.count = count
        return this
    }

    infix fun plus(value: T): ListGenerator<T> {
        additionalValues.add(value)
        return this
    }

    fun copy(): ListGenerator<T> {
        val new = ListGenerator(count, baseGenerator)
        new.staticValue = staticValue
        new.additionalValues = additionalValues
        return new
    }
}
