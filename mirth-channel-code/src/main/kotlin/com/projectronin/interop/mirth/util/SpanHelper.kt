package com.projectronin.interop.mirth.util

import datadog.trace.api.DDTags
import io.opentracing.util.GlobalTracer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Runs the method in a span associated to [runningClass] and [method].
 */
fun <T> runInSpan(
    runningClass: KClass<*>,
    method: KFunction<*>,
    block: () -> T,
): T {
    val tracer = GlobalTracer.get()

    val className = runningClass.simpleName ?: runningClass.java.name
    val methodName = method.name
    val span =
        tracer.buildSpan("mirth.channel").withTag(DDTags.SERVICE_NAME, "mirth")
            .withTag(DDTags.RESOURCE_NAME, "$className.$methodName").start()
    try {
        return tracer.activateSpan(span).use {
            block.invoke()
        }
    } finally {
        span.finish()
    }
}
