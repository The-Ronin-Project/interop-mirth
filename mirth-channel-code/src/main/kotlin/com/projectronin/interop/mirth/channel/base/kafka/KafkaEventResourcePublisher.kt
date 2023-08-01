package com.projectronin.interop.mirth.channel.base.kafka

import com.github.benmanes.caffeine.cache.Caffeine
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.base.BaseProfile
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
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

        // if the resource request had an upstream resource,
        // grab that, so we can place it in the data map for easier viewing in mirth
        val newMap = resourceLoadRequest.getSourceReference()
            ?.let { mapOf(MirthKey.EVENT_METADATA_SOURCE.code to "${it.resourceType}/${it.id}") }
            ?: emptyMap()

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
        val (resources, previouslyCachedCount) =
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
                detailedMessage = resources.truncateList(),
                message = "This message is meant only for internal processing.",
                dataMap = newMap
            )
        }
        val cachedResourcesCount = allRequestKeys.size - requestKeysToProcess.size + previouslyCachedCount
        val cachedMessage =
            if (cachedResourcesCount > 0) " $cachedResourcesCount resource(s) were already processed this run." else ""

        if (resources.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = "No new resources retrieved from EHR.$cachedMessage",
                message = "No resources",
                dataMap = newMap
            )
        }

        val transformedResources = resources.mapNotNull {
            transformManager.transformResource(it, profileTransformer, tenant)
        }
        if (transformedResources.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = resources.truncateList(),
                message = "Failed to transform ${resources.size} resource(s)",
                dataMap = newMap + mapOf(MirthKey.FAILURE_COUNT.code to resources.size)
            )
        }
        // if some of our uncachedResources failed to transform, we should alert, but publish the ones that worked
        if (transformedResources.size != resources.size) {
            logger.error {
                "Received ${resources.size} resources from EHR but only transformed ${transformedResources.size} " +
                    "for tenant: $tenantMnemonic.\n" +
                    "Resources received: ${resources.map { it.id?.value }} \n" +
                    "Resources transformed: ${transformedResources.map { it.id?.value }}"
            }
        }

        val postTransformedResources = postTransform(tenant, transformedResources, vendorFactory)

        // publish says it returns a boolean, but actually throws an error if there was a problem
        val publishResult = runCatching {
            publishService.publishFHIRResources(
                tenantMnemonic,
                postTransformedResources,
                resourceLoadRequest.getUpdatedMetadata(),
                if (resourceLoadRequest.skipKafkaPublishing) null else resourceLoadRequest.dataTrigger
            )
        }.fold(
            onSuccess = { it },
            onFailure = { false }
        )
        if (!publishResult) {
            // in the event of a failure to publish we want to invalidate the keys in we put in during loadResources
            // some of these keys might have been processed, but we don't have access to know which keys failed and which
            // succeeded here
            val keys = postTransformedResources.map {
                ResourceRequestKey(
                    resourceLoadRequest.metadata.runId,
                    ResourceType.valueOf(it.resourceType),
                    resourceLoadRequest.tenant,
                    it.id!!.value!!.unlocalize(tenant)
                )
            }
            logger.debug { "Invalidating ${keys.size} keys" }
            processedResourcesCache.invalidateAll(keys)
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = postTransformedResources.truncateList(),
                message = "Failed to publish ${postTransformedResources.size} resource(s)",
                dataMap = newMap + mapOf(MirthKey.FAILURE_COUNT.code to resources.size)
            )
        } else {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = postTransformedResources.truncateList(),
                message = "Published ${postTransformedResources.size} resource(s).$cachedMessage",
                dataMap = newMap +
                    mapOf(MirthKey.FAILURE_COUNT.code to (resources.size - postTransformedResources.size)) +
                    mapOf(MirthKey.RESOURCE_COUNT.code to postTransformedResources.size)
            )
        }
    }

    /**
     * Filters out any provided request keys that have already been processed.
     */
    private fun filterRequestKeys(requestKeys: List<ResourceRequestKey>): List<ResourceRequestKey> {
        // Determine which request keys we have already processed through the system.
        val uncachedRequestKeys = requestKeys.filter { processedResourcesCache.getIfPresent(it) == null }

        logger.debug {
            val cachedKeys = requestKeys - uncachedRequestKeys
            "Identified ${cachedKeys.size} cached request keys: ${cachedKeys.joinToString(", ")}."
        }
        return uncachedRequestKeys
    }

    /**
     * Loads the resources associated to the [requestKeys] from [resourceLoadRequest], returning them along with a count
     * of the number of resources that were filtered. This method may also filter out previously processed responses
     * based on the publisher's configuration.
     */
    private fun loadResources(
        resourceLoadRequest: ResourceLoadRequest<T>,
        requestKeys: List<ResourceRequestKey>
    ): Pair<List<T>, Int> {
        val resources = resourceLoadRequest.loadResources(requestKeys)

        // If this publisher has requested that we cache and compare results, we need to determine if we've previously
        // processed the reply or not. If we have, we will filter out the value now. If we have not, we will add it to
        // the cache and continue processing.
        val uncachedResources = if (cacheAndCompareResults) {
            resources.mapNotNull {
                val key = ResourceRequestKey(
                    resourceLoadRequest.metadata.runId,
                    ResourceType.valueOf(it.resourceType),
                    resourceLoadRequest.tenant,
                    it.id!!.value!!
                )
                if (processedResourcesCache.getIfPresent(key) == null) {
                    logger.info { "Caching $key" }
                    processedResourcesCache.put(key, true)
                    it
                } else {
                    logger.info { "$key was already cached, so not returning for processing" }
                    null
                }
            }
        } else {
            // Since the publisher did not want to cache and compare results, we just use the original resources.
            resources
        }

        // Regardless of whether we got a response or not, we need to cache the request keys we just used
        logger.debug { "Caching searched keys: ${requestKeys.joinToString(", ")}" }
        requestKeys.forEach { processedResourcesCache.put(it, true) }

        return Pair(uncachedResources, resources.size - uncachedResources.size)
    }

    private fun List<Resource<*>>.truncateList(): String {
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
    abstract fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<T>

    /**
     * Allows post-processing of transformed resources before publishing
     */
    open fun postTransform(tenant: Tenant, transformedList: List<T>, vendorFactory: VendorFactory): List<T> =
        transformedList
}
