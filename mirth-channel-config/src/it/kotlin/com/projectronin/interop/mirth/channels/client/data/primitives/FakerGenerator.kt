package com.projectronin.interop.mirth.channels.client.data.primitives

import com.projectronin.interop.mirth.channels.client.data.Generator
import kotlin.reflect.KFunction

class FakerGenerator(private val function: KFunction<String>) : Generator<String>() {
    override fun generateInternal(): String? = function.call()
}
