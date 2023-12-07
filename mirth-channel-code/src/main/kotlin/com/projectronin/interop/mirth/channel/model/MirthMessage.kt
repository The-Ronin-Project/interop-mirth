package com.projectronin.interop.mirth.channel.model

/**
 * Used to pass Mirth-friendly data in between Kotlin and Mirth.
 */
data class MirthMessage(
    val message: String = "",
    val dataMap: Map<String, Any> = emptyMap(),
)

fun emptyMirthMessage(): MirthMessage {
    return MirthMessage()
}
