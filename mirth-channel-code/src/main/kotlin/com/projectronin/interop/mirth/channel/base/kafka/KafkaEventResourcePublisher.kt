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
import com.projectronin.interop.kafka.model.PublishResourceWrapper
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
import com.projectronin.interop.publishers.model.PublishResponse
import com.projectronin.interop.rcdm.transform.TransformManager
import com.projectronin.interop.rcdm.transform.model.TransformResponse
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.beans.factory.annotation.Value
import java.util.concurrent.TimeUnit

abstract class KafkaEventResourcePublisher<T : Resource<T>>(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val transformManager: TransformManager,
    private val publishService: PublishService,
    @Value("\${kafka.truncate.limit:#{100}}")
    private val truncateLimit: Int = 100,
) : TenantlessDestinationService() {
    /**
     * If true, we cache and compare the retrieved resources against the cache. By default, this is false, but if a
     * publisher is ever dealing with loads that may result in the same resource being returned from different initial
     * contexts, this can help reduce the downstream burden of transforming and publishing those duplicate retrieved resources.
     */
    protected open val cacheAndCompareResults: Boolean = false
    private val processedResourcesCache =
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).softValues().build<ResourceRequestKey, Boolean>()

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthResponse {
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val eventClassName = sourceMap[MirthKey.KAFKA_EVENT.code] ?: throw MapVariableMissing("Missing Event Name")
        val resourceLoadRequest = convertEventToRequest(msg, eventClassName as String, vendorFactory, tenant)

        // if the resource request had upstream resources, add a String-representation to the metadata.
        val sourceReferences = resourceLoadRequest.sourceReferences
        val sourceReferenceMap =
            if (sourceReferences.isEmpty()) {
                emptyMap()
            } else {
                mapOf(MirthKey.EVENT_METADATA_SOURCE.code to sourceReferences.joinToString(", ") { "${it.resourceType}/${it.id}" })
            }

        val allRequestKeys = resourceLoadRequest.requestKeys
        if (allRequestKeys.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = "No request keys exist prior to checking the cache",
                message = "No request keys exist prior to checking the cache",
                dataMap = sourceReferenceMap,
            )
        }
        val requestKeysToProcess = filterRequestKeys(allRequestKeys)
        // If there are no request keys, then we've previously done all the work for this request and can return a message indicating such.
        if (requestKeysToProcess.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = "All requested resources have already been processed this run: ${
                    allRequestKeys.joinToString(
                        ", ",
                    )
                }",
                message = "Already processed",
                dataMap = sourceReferenceMap,
            )
        }

        val requestDataMap = sourceReferenceMap + resourceLoadRequest.requestSpecificMirthMetadata

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
                        dataMap = requestDataMap,
                    )
                },
            )
        if (resourceLoadRequest.skipAllPublishing) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = resourcesByKey.truncateList(),
                message = "This message is meant only for internal processing.",
                dataMap = requestDataMap,
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
                dataMap = requestDataMap,
            )
        }

        val minimumRegistryTime = resourceLoadRequest.minimumRegistryCacheTime?.toLocalDateTime()
        val transformedResourcesByKey =
            resourcesByKey.mapValuesNotNull { (_, resources) ->
                resources.mapNotNull { resource ->
                    transformManager.transformResource(
                        resource,
                        tenant,
                        forceCacheReloadTS = minimumRegistryTime,
                    )
                }.ifEmpty { null }
            }
        val totalResourcesCount = resourcesByKey.totalSize()
        if (transformedResourcesByKey.isEmpty()) {
            logger.error {
                "Received $totalResourcesCount resources from EHR but transform failed for all for tenant: $tenantMnemonic.\n" +
                    "Resources received: ${resourcesByKey.ids()}"
            }
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = resourcesByKey.truncateList(),
                message = "Failed to transform $totalResourcesCount resource(s)",
                dataMap = requestDataMap + mapOf(MirthKey.FAILURE_COUNT.code to totalResourcesCount),
            )
        }

        // filter out failed to transform to remove from cache
        val failedToTransform =
            resourcesByKey.filterNot {
                it.key in transformedResourcesByKey.keys
            }
        // clear cache for failed to transform keys and resources
        clearCache(failedToTransform.keys, failedToTransform.values.flatten(), tenant, resourceLoadRequest)

        // if some of our uncachedResources failed to transform, we should alert, but publish the ones that worked
        val totalTransformedResourcesCount = transformedResourcesByKey.totalSize()
        if (totalTransformedResourcesCount != totalResourcesCount) {
            logger.error {
                "Received $totalResourcesCount resources from EHR but only transformed $totalTransformedResourcesCount " +
                    "for tenant: $tenantMnemonic.\n" +
                    "Resources received: ${resourcesByKey.ids()} \n" +
                    "Resources transformed: ${transformedResourcesByKey.resourceIds()}"
            }
        }

        val postTransformedResourcesByKey = postTransform(tenant, transformedResourcesByKey, vendorFactory)
        // filter out failed post transform to remove from cache, create two list, one of keys and one of resources
        val (failedPostTransformKey, failedPostTransformResource) =
            transformedResourcesByKey.filterNot {
                it.key in postTransformedResourcesByKey
            }.toList().let { transformResponse ->
                transformResponse.map { it.first }
                    .toSet() to transformResponse.flatMap { it.second.map { it.resource } }
            }

        if (failedPostTransformKey.isNotEmpty()) {
            // clear cache for failed post transform keys and resources
            clearCache(failedPostTransformKey, failedPostTransformResource, tenant, resourceLoadRequest)
            logger.error {
                "Post transform failed for ${failedPostTransformKey.size} " +
                    "resources for tenant $tenantMnemonic.\n" +
                    "Resources received: ${transformedResourcesByKey.resourceIds()} \n+" +
                    "Resources transformed: ${postTransformedResourcesByKey.resourceIds()}"
            }
        }

        val transformsToPublishByEvent =
            postTransformedResourcesByKey.map { (key, resource) ->
                resourceLoadRequest.eventsByRequestKey[key]!! to resource
            }.groupBy { it.first }.mapValues { e -> e.value.flatMap { v -> v.second } }

        // publish says it returns a boolean, but actually throws an error if there was a problem
        val publishResultsByEvent =
            transformsToPublishByEvent.map { (event, transform) ->
                val dataTrigger =
                    if (event.processDownstreamReferences && !resourceLoadRequest.skipKafkaPublishing) {
                        resourceLoadRequest.dataTrigger
                    } else {
                        null
                    }

                runCatching {
                    publishService.publishResourceWrappers(
                        tenantMnemonic,
                        transform.map { it.toResourceWrapper() },
                        event.getUpdatedMetadata(),
                        dataTrigger,
                    )
                }.fold(
                    onSuccess = { event to it },
                    onFailure = {
                        logger.error(it) { "Failed to publish by event" }
                        event to
                            PublishResponse(
                                successfulIds = emptyList(),
                                failedIds = transform.map { it.resource.id!!.value!! },
                            )
                    },
                )
            }

        val transformedResourcesById =
            postTransformedResourcesByKey.values.flatten().associate { it.resource.id!!.value!! to it.resource }

        val successfullyPublishedTransforms =
            publishResultsByEvent.flatMap { it.second.successfulIds }.map { transformedResourcesById[it]!! }

        val failedPublishEvents = publishResultsByEvent.filterNot { it.second.isSuccess }
        val failedPublishTransforms =
            failedPublishEvents.flatMap { it.second.failedIds }.map { transformedResourcesById[it]!! }

        val failedPublishKeys =
            failedPublishEvents.flatMap { (event, _) -> event.requestKeys.union(requestKeysToProcess) }.toSet()

        // clear cache - resources and keys that failed to publish
        clearCache(failedPublishKeys, failedPublishTransforms, tenant, resourceLoadRequest)

        if (failedPublishTransforms.isNotEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = failedPublishTransforms.truncateList(),
                message =
                    "Successfully published ${successfullyPublishedTransforms.size}, " +
                        "but failed to publish ${failedPublishTransforms.size} resource(s)",
                dataMap =
                    requestDataMap +
                        mapOf(
                            MirthKey.FAILURE_COUNT.code to failedPublishTransforms.size,
                            MirthKey.RESOURCE_COUNT.code to successfullyPublishedTransforms.size,
                        ),
            )
        } else {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = successfullyPublishedTransforms.truncateList(),
                message = "Published ${successfullyPublishedTransforms.size} resource(s).$cachedMessage",
                dataMap =
                    requestDataMap +
                        mapOf(
                            MirthKey.FAILURE_COUNT.code to (totalResourcesCount - successfullyPublishedTransforms.size),
                            MirthKey.RESOURCE_COUNT.code to successfullyPublishedTransforms.size,
                        ),
            )
        }
    }

    /**
     * Clear out cache for failed to transform resources.
     */
    private fun clearCache(
        failedKeys: Set<ResourceRequestKey>,
        failedResources: List<T>,
        tenant: Tenant,
        resourceLoadRequest: ResourceRequest<T, *>,
    ) {
        val keys =
            failedResources.map { transform ->
                val resource = transform as Resource<*>
                ResourceRequestKey(
                    resourceLoadRequest.runId,
                    ResourceType.valueOf(resource.resourceType),
                    resourceLoadRequest.tenant,
                    resource.id!!.value!!.unlocalize(tenant),
                )
            } + failedKeys
        logger.debug { "Invalidating ${failedResources.size} keys" }
        processedResourcesCache.invalidateAll(keys)
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
        requestKeys: List<ResourceRequestKey>,
    ): Pair<Map<ResourceRequestKey, List<T>>, Int> {
        // Load the resources, and remove any cases with empty Lists so we can easily know when values exist or not
        val resourcesByKey =
            resourceRequest.loadResources(requestKeys).filterNot { (_, values) ->
                values.isEmpty()
            }

        // If this publisher has requested that we cache and compare results, we need to determine if we've previously
        // processed the reply or not. If we have, we will filter out the value now. If we have not, we will add it to
        // the cache and continue processing.
        val uncachedResources =
            if (cacheAndCompareResults) {
                resourcesByKey.mapValuesNotNull { (_, resources) ->
                    resources.mapNotNull { resource ->
                        val responseKey =
                            ResourceRequestKey(
                                resourceRequest.runId,
                                ResourceType.valueOf(resource.resourceType),
                                resourceRequest.tenant,
                                resource.id!!.value!!,
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
        return values.flatten().ids()
    }

    private fun Map<*, List<TransformResponse<*>>>.resourceIds(): String {
        return values.flatten().map { it.resource }.ids()
    }

    private fun List<Resource<*>>.ids(): String {
        return joinToString(", ") { it.id!!.value!! }
    }

    private fun Map<*, List<*>>.totalSize(): Int {
        return values.sumOf { it.size }
    }

    private fun Collection<Resource<*>>.truncateList(): String {
        if (this.size < truncateLimit) {
            val list = this.map { it.id?.value }
            return JacksonUtil.writeJsonValue(list)
        }
        return "${this.size} resources"
    }

    private fun Collection<TransformResponse<*>>.truncateResourceList(): String {
        if (this.size < truncateLimit) {
            val list =
                this.map { it.resource.id?.value }
            return JacksonUtil.writeJsonValue(list)
        }
        return "${this.size} resources"
    }

    private fun TransformResponse<T>.toResourceWrapper() = PublishResourceWrapper(resource, embeddedResources)

    /**
     * Turns any type of event that a channel may be subscribed into a class that should be able to handle a request
     * to loadResources of type [T]
     */
    private fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant,
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
        tenant: Tenant,
    ): PublishResourceRequest<T>

    abstract fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<T>

    /**
     * Allows post-processing of transformed resources before publishing
     */
    open fun postTransform(
        tenant: Tenant,
        transformedResourcesByKey: Map<ResourceRequestKey, List<TransformResponse<T>>>,
        vendorFactory: VendorFactory,
    ): Map<ResourceRequestKey, List<TransformResponse<T>>> = transformedResourcesByKey
}
