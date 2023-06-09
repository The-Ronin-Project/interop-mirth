package com.projectronin.interop.mirth.channel.util

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.mirth.channel.enums.MirthKey
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Generates a new [Metadata] with a random UUID and the current time.
 */
fun generateMetadata(): Metadata =
    Metadata(runId = UUID.randomUUID().toString(), runDateTime = OffsetDateTime.now(ZoneOffset.UTC))

/**
 * Generates a new [Metadata] with a random UUID and the current time and serializes it.
 * This should only be used for those cases where the Metadata is simply being created to be passed through Mirth.
 * If you need an actual instance of Metadata for active processing, please use [generateMetadata]
 */
fun generateSerializedMetadata(): String = serialize(generateMetadata())

/**
 * Serializes the [metadata] for sending around in Mirth messages.
 */
fun serialize(metadata: Metadata): String = JacksonManager.objectMapper.writeValueAsString(metadata)

/**
 * Deserializes the [metadataString] into a [Metadata]
 */
fun deserialize(metadataString: String): Metadata =
    JacksonManager.objectMapper.readValue(metadataString)

/**
 * Gets the [Metadata] from the [sourceMap].
 */
fun getMetadata(sourceMap: Map<String, Any>): Metadata =
    deserialize(sourceMap[MirthKey.EVENT_METADATA.code] as String)
