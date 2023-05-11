package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.mirth.channel.enums.MirthKey

/**
 * Gets the [Metadata] from the [sourceMap].
 */
fun getMetadata(sourceMap: Map<String, Any>): Metadata =
    sourceMap[MirthKey.EVENT_METADATA.code] as Metadata
