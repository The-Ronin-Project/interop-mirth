package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.interop.common.collection.mapListValues
import com.projectronin.interop.fhir.r4.resource.Resource
import java.time.OffsetDateTime

/**
 * Base class for requesting resources based off the reference IDs on a published resource.
 */
abstract class PublishReferenceResourceRequest<T : Resource<T>> : PublishResourceRequest<T>() {
    override fun loadResourcesForIds(
        requestFhirIds: List<String>,
        startDate: OffsetDateTime?,
        endDate: OffsetDateTime?
    ): Map<String, List<T>> {
        return fhirService.getByIDs(tenant, requestFhirIds).mapListValues()
    }
}
