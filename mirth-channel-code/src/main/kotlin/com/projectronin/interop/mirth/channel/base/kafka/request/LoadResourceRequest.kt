package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.interop.common.collection.mapListValues
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.kafka.event.LoadResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.tenant.config.model.Tenant
import java.time.OffsetDateTime

/**
 * Base class for resource requests based on [InteropResourceLoadV1] events.
 */
abstract class LoadResourceRequest<T : Resource<T>>(
    loadEvents: List<InteropResourceLoadV1>,
    final override val tenant: Tenant
) : ResourceRequest<T, InteropResourceLoadV1>() {
    final override val sourceEvents: List<ResourceEvent<InteropResourceLoadV1>> =
        loadEvents.map { LoadResourceEvent(it, tenant) }

    final override val dataTrigger: DataTrigger = when (loadEvents.first().dataTrigger) {
        InteropResourceLoadV1.DataTrigger.adhoc -> DataTrigger.AD_HOC
        InteropResourceLoadV1.DataTrigger.nightly -> DataTrigger.NIGHTLY
        InteropResourceLoadV1.DataTrigger.backfill -> DataTrigger.BACKFILL
        null -> throw IllegalStateException("Received a null data trigger which cannot be transformed to a known value")
    }

    override fun loadResourcesForIds(requestFhirIds: List<String>, startDate: OffsetDateTime?, endDate: OffsetDateTime?): Map<String, List<T>> {
        return fhirService.getByIDs(tenant, requestFhirIds).mapListValues()
    }
}
