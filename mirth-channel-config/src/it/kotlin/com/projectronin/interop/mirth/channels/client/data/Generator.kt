package com.projectronin.interop.mirth.channels.client.data

abstract class Generator<T> {
    private var staticValue: T? = null

    protected abstract fun generateInternal(): T?

    fun generate(): T? = staticValue ?: generateInternal()

    infix fun of(value: T) {
        staticValue = value
    }
}
