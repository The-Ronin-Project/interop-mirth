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

    val serviceTag = runningClass.simpleName ?: runningClass.java.name
    val methodTag = method.name
    val span =
        tracer.buildSpan("$serviceTag.$methodTag").withTag(DDTags.SERVICE_NAME, serviceTag)
            .withTag(DDTags.RESOURCE_NAME, methodTag).start()
    try {
        return tracer.activateSpan(span).use {
            block.invoke()
        }
    } finally {
        span.finish()
    }
}
