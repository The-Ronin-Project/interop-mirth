package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Resource
import kotlin.reflect.KClass

/**
 * Base class for [InteropResourcePublishV1] events.
 * If this event is relying on the ID of the reference on the event, you should use [IdBasedPublishResourceEvent] instead.
 */
abstract class PublishResourceEvent<S : Resource<S>>(
    override val sourceEvent: InteropResourcePublishV1,
    sourceClass: KClass<S>
) : ResourceEvent<InteropResourcePublishV1> {
    override val metadata: Metadata by lazy { sourceEvent.metadata }

    // add the new reference to the end of the list
    final override fun getUpdatedMetadata(): Metadata =
        metadata.copy(
            upstreamReferences = (metadata.upstreamReferences ?: emptyList()) +
                listOf(getSourceReference())
        )

    // source reference is just the id and type of the source event
    final override fun getSourceReference(): Metadata.UpstreamReference = Metadata.UpstreamReference(
        id = sourceResource.id!!.value!!,
        resourceType = sourceEvent.resourceType
    )

    internal val sourceResource: S by lazy { JacksonUtil.readJsonObject(sourceEvent.resourceJson, sourceClass) }
}
