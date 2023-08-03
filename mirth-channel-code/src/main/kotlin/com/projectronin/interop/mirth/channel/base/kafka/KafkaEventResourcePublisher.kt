package com.projectronin.interop.mirth.channel.base.kafka

import com.github.benmanes.caffeine.cache.Caffeine
import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.collection.mapValuesNotNull
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.base.BaseProfile
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.channel.util.unlocalize
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import java.util.concurrent.TimeUnit

abstract class KafkaEventResourcePublisher<T : Resource<T>>(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val transformManager: TransformManager,
    private val publishService: PublishService,
    private val profileTransformer: BaseProfile<T>
) : TenantlessDestinationService() {
    /**
     * If true, we cache and compare the retrieved resources against the cache. By default, this is false, but if a
     * publisher is ever dealing with loads that may result in the same resource being returned from different initial
     * contexts, this can help reduce the downstream burden of transforming and publishing those duplicate retrieved resources.
     */
    protected open val cacheAndCompareResults: Boolean = false
    private val processedResourcesCache =
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).build<ResourceRequestKey, Boolean>()

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val eventClassName = sourceMap[MirthKey.KAFKA_EVENT.code] ?: throw MapVariableMissing("Missing Event Name")
        val resourceLoadRequest = convertEventToRequest(msg, eventClassName as String, vendorFactory, tenant)

        // if the resource request had upstream resources, add a String-representation to the metadata.
        val sourceReferences = resourceLoadRequest.sourceReferences
        val newMap = if (sourceReferences.isEmpty()) {
            emptyMap()
        } else {
            mapOf(MirthKey.EVENT_METADATA_SOURCE.code to sourceReferences.joinToString(", ") { "${it.resourceType}/${it.id}" })
        }

        val allRequestKeys = resourceLoadRequest.requestKeys
        val requestKeysToProcess = filterRequestKeys(allRequestKeys)
        // If there are no request keys, then we've previously done all the work for this request and can return a message indicating such.
        if (requestKeysToProcess.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = "All requested resources have already been processed this run: ${
                allRequestKeys.joinToString(
                    ", "
                )
                }",
                message = "Already processed",
                dataMap = newMap
            )
        }

        // Now we're going to go and load all the requests we did not have cached
        val (resourcesByKey, previouslyCachedCount) =
            runCatching {
                loadResources(resourceLoadRequest, requestKeysToProcess)
            }.fold(
                onSuccess = { it },
                onFailure = {
                    // We received an error, so we need to return a response indicating the error.
                    logger.error(it) { "Failed to retrieve resources from EHR" }
                    return MirthResponse(
                        status = MirthResponseStatus.ERROR,
                        detailedMessage = it.message,
                        message = "Failed EHR Call",
                        dataMap = newMap
                    )
                }
            )
        if (resourceLoadRequest.skipAllPublishing) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = resourcesByKey.truncateList(),
                message = "This message is meant only for internal processing.",
                dataMap = newMap
            )
        }
        val cachedResourcesCount = allRequestKeys.size - requestKeysToProcess.size + previouslyCachedCount
        val cachedMessage =
            if (cachedResourcesCount > 0) " $cachedResourcesCount resource(s) were already processed this run." else ""

        if (resourcesByKey.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = "No new resources retrieved from EHR.$cachedMessage",
                message = "No resources",
                dataMap = newMap
            )
        }

        val transformedResourcesByKey = resourcesByKey.mapValuesNotNull { (_, resources) ->
            resources.mapNotNull { resource ->
                transformManager.transformResource(resource, profileTransformer, tenant)
            }.ifEmpty { null }
        }
        if (transformedResourcesByKey.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = resourcesByKey.truncateList(),
                message = "Failed to transform ${resourcesByKey.totalSize()} resource(s)",
                dataMap = newMap + mapOf(MirthKey.FAILURE_COUNT.code to resourcesByKey.totalSize())
            )
        }
        // if some of our uncachedResources failed to transform, we should alert, but publish the ones that worked
        if (transformedResourcesByKey.totalSize() != resourcesByKey.totalSize()) {
            logger.error {
                "Received ${resourcesByKey.totalSize()} resources from EHR but only transformed ${transformedResourcesByKey.totalSize()} " +
                    "for tenant: $tenantMnemonic.\n" +
                    "Resources received: ${resourcesByKey.ids()} \n" +
                    "Resources transformed: ${transformedResourcesByKey.ids()}"
            }
        }

        val postTransformedResourcesByKey = postTransform(tenant, transformedResourcesByKey, vendorFactory)

        val resourcesToPublishByEvent = postTransformedResourcesByKey.map { (key, resource) ->
            resourceLoadRequest.eventsByRequestKey[key]!! to resource
        }.groupBy { it.first }.mapValues { e -> e.value.flatMap { v -> v.second } }

        // publish says it returns a boolean, but actually throws an error if there was a problem
        val publishResultsByEvent =
            resourcesToPublishByEvent.map { (event, resources) ->
                runCatching {
                    publishService.publishFHIRResources(
                        tenantMnemonic,
                        resources,
                        event.getUpdatedMetadata(),
                        if (resourceLoadRequest.skipKafkaPublishing) null else resourceLoadRequest.dataTrigger
                    )
                }.fold(
                    onSuccess = { event to it },
                    onFailure = { event to false }
                )
            }

        val successfullyPublishedResources = publishResultsByEvent.filter { it.second }
            .flatMap { resourcesToPublishByEvent[it.first] ?: emptyList() }
        val failedPublishResources = publishResultsByEvent.filterNot { it.second }
            .flatMap { resourcesToPublishByEvent[it.first] ?: emptyList() }

        if (failedPublishResources.isNotEmpty()) {
            // in the event of a failure to publish we want to invalidate the keys we put in during loadResources
            // some of these keys were successes, so we're only operating on the failures here. But we do need the successes for informational data
            val keys = failedPublishResources.map { resource ->
                ResourceRequestKey(
                    resourceLoadRequest.runId,
                    ResourceType.valueOf(resource.resourceType),
                    resourceLoadRequest.tenant,
                    resource.id!!.value!!.unlocalize(tenant)
                )
            }
            logger.debug { "Invalidating ${keys.size} keys" }
            processedResourcesCache.invalidateAll(keys)
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = failedPublishResources.truncateList(),
                message = "Successfully published ${successfullyPublishedResources.size}, but failed to publish ${failedPublishResources.size} resource(s)",
                dataMap = newMap + mapOf(
                    MirthKey.FAILURE_COUNT.code to failedPublishResources.size,
                    MirthKey.RESOURCE_COUNT.code to successfullyPublishedResources.size
                )
            )
        } else {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = successfullyPublishedResources.truncateList(),
                message = "Published ${successfullyPublishedResources.size} resource(s).$cachedMessage",
                dataMap = newMap +
                    mapOf(
                        MirthKey.FAILURE_COUNT.code to (resourcesByKey.size - successfullyPublishedResources.size),
                        MirthKey.RESOURCE_COUNT.code to successfullyPublishedResources.size
                    )
            )
        }
    }

    /**
     * Filters out any provided request keys that have already been processed.
     */
    private fun filterRequestKeys(requestKeys: Set<ResourceRequestKey>): List<ResourceRequestKey> {
        // Determine which request keys we have already processed through the system.
        val uncachedRequestKeys = requestKeys.filter { processedResourcesCache.getIfPresent(it) == null }

        logger.debug {
            val cachedKeys = requestKeys - uncachedRequestKeys
            "Identified ${cachedKeys.size} cached request keys: ${cachedKeys.joinToString(", ")}."
        }
        return uncachedRequestKeys
    }

    /**
     * Loads the resources associated to the [requestKeys] from [resourceRequest], returning them along with a count
     * of the number of resources that were filtered. This method may also filter out previously processed responses
     * based on the publisher's configuration.
     */
    private fun loadResources(
        resourceRequest: ResourceRequest<T, *>,
        requestKeys: List<ResourceRequestKey>
    ): Pair<Map<ResourceRequestKey, List<T>>, Int> {
        val resourcesByKey = resourceRequest.loadResources(requestKeys)

        // If this publisher has requested that we cache and compare results, we need to determine if we've previously
        // processed the reply or not. If we have, we will filter out the value now. If we have not, we will add it to
        // the cache and continue processing.
        val uncachedResources = if (cacheAndCompareResults) {
            resourcesByKey.mapValuesNotNull { (_, resources) ->
                resources.mapNotNull { resource ->
                    val responseKey = ResourceRequestKey(
                        resourceRequest.runId,
                        ResourceType.valueOf(resource.resourceType),
                        resourceRequest.tenant,
                        resource.id!!.value!!
                    )
                    if (processedResourcesCache.getIfPresent(responseKey) == null) {
                        logger.info { "Caching $responseKey" }
                        processedResourcesCache.put(responseKey, true)
                        resource
                    } else {
                        logger.info { "$responseKey was already cached, so not returning for processing" }
                        null
                    }
                }.ifEmpty { null }
            }
        } else {
            // Since the publisher did not want to cache and compare results, we just use the original resources.
            resourcesByKey
        }

        // Regardless of whether we got a response or not, we need to cache the request keys we just used
        logger.debug { "Caching searched keys: ${requestKeys.joinToString(", ")}" }
        requestKeys.forEach { processedResourcesCache.put(it, true) }

        return Pair(uncachedResources, resourcesByKey.totalSize() - uncachedResources.totalSize())
    }

    private fun Map<*, List<Resource<*>>>.truncateList(): String {
        return values.flatten().truncateList()
    }

    private fun Map<*, List<Resource<*>>>.ids(): String {
        return values.flatten().joinToString(", ") { it.id!!.value!! }
    }

    private fun Map<*, List<Resource<*>>>.totalSize(): Int {
        return values.sumOf { it.size }
    }

    private fun Collection<Resource<*>>.truncateList(): String {
        val list = when {
            this.size > 5 -> this.map { it.id?.value }
            else -> this
        }
        return JacksonUtil.writeJsonValue(list)
    }

    /**
     * Turns any type of event that a channel may be subscribed into a class that should be able to handle a request
     * to loadResources of type [T]
     */
    private fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceRequest<T, *> {
        logger.info { "Processing event for $eventClassName: $serializedEvent" }
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val events = JacksonUtil.readJsonList(serializedEvent, InteropResourcePublishV1::class)
                convertPublishEventsToRequest(events, vendorFactory, tenant)
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val events = JacksonUtil.readJsonList(serializedEvent, InteropResourceLoadV1::class)
                convertLoadEventsToRequest(events, vendorFactory, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    abstract fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<T>

    abstract fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<T>

    /**
     * Allows post-processing of transformed resources before publishing
     */
    open fun postTransform(
        tenant: Tenant,
        transformedResourcesByKey: Map<ResourceRequestKey, List<T>>,
        vendorFactory: VendorFactory
    ): Map<ResourceRequestKey, List<T>> =
        transformedResourcesByKey
}
