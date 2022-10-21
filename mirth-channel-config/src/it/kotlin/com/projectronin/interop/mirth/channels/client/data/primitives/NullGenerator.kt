package com.projectronin.interop.mirth.channels.client.data.primitives

import com.projectronin.interop.mirth.channels.client.data.Generator

open class NullGenerator<T> : Generator<T>() {
    override fun generateInternal(): T? = null
}
