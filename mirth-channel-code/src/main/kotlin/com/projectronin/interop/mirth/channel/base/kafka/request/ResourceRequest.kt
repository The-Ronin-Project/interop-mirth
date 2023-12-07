package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.tenant.config.model.Tenant
import mu.KotlinLogging
import java.time.OffsetDateTime

/**
 * Base class for all resource requests, where [T] is the type of the resource being requested and [E] is the type of event that triggered the request.
 */
abstract class ResourceRequest<T : Resource<T>, E> {
    val logger = KotlinLogging.logger { }

    /**
     * The source events backing this request.
     */
    abstract val sourceEvents: List<ResourceEvent<E>>

    /**
     * The data trigger associated to the events
     */
    abstract val dataTrigger: DataTrigger

    /**
     * The FHIR service responsible for loading the requested resources.
     */
    abstract val fhirService: FHIRService<T>

    /**
     * The tenant for whom the requests are being made.
     */
    abstract val tenant: Tenant

    /**
     * The run ID associated to the source events.
     */
    val runId: String by lazy { sourceEvents.first().metadata.runId }

    /**
     * Map of the [requestKeys] keyed to the [sourceEvents] from which they were requested.
     */
    val eventsByRequestKey: Map<ResourceRequestKey, ResourceEvent<E>> by lazy {
        sourceEvents.flatMap { e -> e.requestKeys.map { k -> k to e } }.associate { it }
    }

    /**
     * The Set of all request keys from the [sourceEvents]
     */
    val requestKeys: Set<ResourceRequestKey> by lazy { eventsByRequestKey.keys }

    /**
     * The List of all source references for this request.
     */
    val sourceReferences: List<Metadata.UpstreamReference> by lazy { sourceEvents.mapNotNull { it.getSourceReference() } }

    val minimumRegistryCacheTime: OffsetDateTime? by lazy {
        // We want the max here because if 2 different events have differing minimums, the most recent minimum is the true minimum.
        sourceEvents.mapNotNull { it.minimumRegistryCacheTime }.maxOrNull()
    }

    /**
     * True if the resources should be loaded, but no publishing should occur for this request.
     */
    open val skipAllPublishing: Boolean = false

    /**
     * True if no internal Kafka events should be published based off this request.
     */
    open val skipKafkaPublishing: Boolean = false

    /**
     * Loads the resources for the supplied [requestFhirIds] and returns them keyed by their supplied ID.
     */
    abstract fun loadResourcesForIds(
        requestFhirIds: List<String>,
        startDate: OffsetDateTime? = null,
        endDate: OffsetDateTime? = null,
    ): Map<String, List<T>>

    /**
     * Loads the resources for the supplied [requestKeys] and returns them keyed by their supplied key.
     */
    open fun loadResources(requestKeys: List<ResourceRequestKey>): Map<ResourceRequestKey, List<T>> {
        if (requestKeys.isEmpty()) {
            return emptyMap()
        }
        val partition = requestKeys.partition { it.dateRange == null }
        val undatedRequests = partition.first
        val undatedKeysByFhirID = undatedRequests.associateBy { it.unlocalizedResourceId }
        logger.debug { "undatedKeysByFhirID $undatedKeysByFhirID " }
        val undatedResourcesByFhirID = loadResourcesForIds(undatedKeysByFhirID.keys.toList())
        val undatedResourceMap = undatedResourcesByFhirID.mapKeys { (fhirId, _) -> undatedKeysByFhirID[fhirId]!! }

        // for backfills we can group the request by their date range
        // for each date range we'll do a version of the undated request but then we need to fold those separate maps back
        // into one large map that we can add to our initial request
        val backfillRequests = partition.second
        logger.debug { "backfillRequests $backfillRequests " }

        val backfillMap =
            backfillRequests.groupBy { it.dateRange!! }.flatMap { datedRequestKeyGroup ->
                val requestsByFhirID = datedRequestKeyGroup.value.associateBy { it.unlocalizedResourceId }
                logger.debug { "backfillMap $requestsByFhirID " }
                val resourcesByFhirID =
                    loadResourcesForIds(requestsByFhirID.keys.toList(), datedRequestKeyGroup.key.first, datedRequestKeyGroup.key.second)
                resourcesByFhirID.mapKeys { (fhirId, _) -> requestsByFhirID[fhirId]!! }.toList()
            }.toMap()
        return undatedResourceMap + backfillMap
    }
}
