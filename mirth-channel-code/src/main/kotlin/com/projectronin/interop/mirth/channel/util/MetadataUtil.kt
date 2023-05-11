package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.Metadata
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Generates a new [Metadata] with a random UUID and the current time.
 */
fun generateMetadata(): Metadata =
    Metadata(runId = UUID.randomUUID().toString(), runDateTime = OffsetDateTime.now(ZoneOffset.UTC))
