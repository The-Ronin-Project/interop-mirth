package com.projectronin.interop.mirth.channel.model

/**
 * Used to pass Mirth-friendly data in between Kotlin and Mirth.
 * The [result] should be set to true when the message should continue processing and false when it should stop
 */
data class MirthFilterResponse(
    val result: Boolean,
    val dataMap: Map<String, Any> = emptyMap(),
)
