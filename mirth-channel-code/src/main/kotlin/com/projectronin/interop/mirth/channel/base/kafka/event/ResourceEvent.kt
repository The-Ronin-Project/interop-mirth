package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import java.time.OffsetDateTime

/**
 * Interface representing a specific resource event.
 */
interface ResourceEvent<T> {
    /**
     * The source event.
     */
    val sourceEvent: T

    /**
     * The Set of keys representing the distinct items requested by this event.
     */
    val requestKeys: Set<ResourceRequestKey>

    /**
     * The metadata on the event
     */
    val metadata: Metadata

    /**
     * True if downstream references should be processed; otherwise, false.
     */
    val processDownstreamReferences: Boolean

    /**
     * The minimum registry cache time for this event, if one is known.
     */
    val minimumRegistryCacheTime: OffsetDateTime?

    /**
     * Returns the updated [Metadata] after including the [getSourceReference] from this event.
     */
    fun getUpdatedMetadata(): Metadata

    /**
     * Returns the source reference backing this event, if one exists.
     */
    fun getSourceReference(): Metadata.UpstreamReference?
}
