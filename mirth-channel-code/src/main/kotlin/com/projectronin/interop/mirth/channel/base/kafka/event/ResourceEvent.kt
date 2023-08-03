package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey

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
     * Returns the updated [Metadata] after including the [getSourceReference] from this event.
     */
    fun getUpdatedMetadata(): Metadata

    /**
     * Returns the source reference backing this event, if one exists.
     */
    fun getSourceReference(): Metadata.UpstreamReference?
}
