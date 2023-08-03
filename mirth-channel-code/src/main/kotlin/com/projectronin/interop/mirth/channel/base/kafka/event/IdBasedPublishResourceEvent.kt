package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import kotlin.reflect.KClass

/**
 * Base class for [InteropResourcePublishV1] events where the request is based off the ID of the resource that was published.
 */
abstract class IdBasedPublishResourceEvent<S : Resource<S>>(
    sourceEvent: InteropResourcePublishV1,
    tenant: Tenant,
    sourceClass: KClass<S>
) : PublishResourceEvent<S>(sourceEvent, sourceClass) {
    override val requestKeys: Set<ResourceRequestKey> by lazy {
        setOf(
            ResourceRequestKey(
                metadata.runId,
                sourceEvent.resourceType,
                tenant,
                sourceResource.id!!.value!!
            )
        )
    }
}
