package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.model.DataTrigger

/**
 * Base class for resource requests based off [InteropResourcePublishV1] events.
 * If you resources are being loaded based off references on the published event, consider using [PublishReferenceResourceRequest] as your base.
 */
abstract class PublishResourceRequest<T : Resource<T>> : ResourceRequest<T, InteropResourcePublishV1>() {
    final override val dataTrigger: DataTrigger by lazy {
        when (sourceEvents.first().sourceEvent.dataTrigger) {
            InteropResourcePublishV1.DataTrigger.adhoc -> DataTrigger.AD_HOC
            InteropResourcePublishV1.DataTrigger.nightly -> DataTrigger.NIGHTLY
            InteropResourcePublishV1.DataTrigger.backfill -> DataTrigger.BACKFILL
            null -> throw IllegalStateException("Received a null data trigger which cannot be transformed to a known value")
        }
    }
}
