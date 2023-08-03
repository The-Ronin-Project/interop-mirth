package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import java.time.OffsetDateTime

/**
 * Class representing [InteropResourceLoadV1] events.
 */
class LoadResourceEvent(
    override val sourceEvent: InteropResourceLoadV1,
    tenant: Tenant
) : ResourceEvent<InteropResourceLoadV1> {
    override val metadata: Metadata = sourceEvent.metadata
    override val processDownstreamReferences: Boolean = !(sourceEvent.flowOptions?.disableDownstreamResources ?: false)
    override val minimumRegistryCacheTime: OffsetDateTime? = sourceEvent.flowOptions?.normalizationRegistryMinimumTime

    override val requestKeys: Set<ResourceRequestKey> =
        setOf(
            ResourceRequestKey(
                metadata.runId,
                sourceEvent.resourceType,
                tenant,
                sourceEvent.resourceFHIRId
            )
        )

    // A load event is the start of the run, so its metadata doesn't need to change
    override fun getUpdatedMetadata(): Metadata = metadata

    // and it doesn't have any upstream references
    override fun getSourceReference(): Metadata.UpstreamReference? = null
}
