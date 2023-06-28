package com.projectronin.interop.mirth.channel.base

import com.github.benmanes.caffeine.cache.Caffeine
import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.base.BaseProfile
import com.projectronin.interop.kafka.model.DataTrigger
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
                message = "Already processed"
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
                        message = "Failed EHR Call"
                    )
                }
            )

        val cachedResourcesCount = allRequestKeys.size - requestKeysToProcess.size + previouslyCachedCount
        val cachedMessage =
            if (cachedResourcesCount > 0) " $cachedResourcesCount resource(s) were already processed this run." else ""

        if (resources.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = "No new resources retrieved from EHR.$cachedMessage",
                message = "No resources"
            )
        }

        val transformedResources = resources.mapNotNull {
            transformManager.transformResource(it, profileTransformer, tenant)
        }
        if (transformedResources.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = resources.truncateList(),
                message = "Failed to transform ${resources.size} resource(s)"
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

        // publish says it returns a boolean, but actually throws an error if there was a problem
        val publishResult = runCatching {
            publishService.publishFHIRResources(
                tenantMnemonic,
                transformedResources,
                resourceLoadRequest.metadata,
                resourceLoadRequest.dataTrigger
            )
        }.fold(
            onSuccess = { it },
            onFailure = { false }
        )
        if (!publishResult) {
            // in the event of a failure to publish we want to invalidate the keys in we put in during loadResources
            // some of these keys might have been processed, but we don't have access to know which keys failed and which
            // succeeded here
            val keys = transformedResources.map {
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
                detailedMessage = transformedResources.truncateList(),
                message = "Failed to publish ${transformedResources.size} resource(s)"
            )
        } else {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = transformedResources.truncateList(),
                message = "Published ${transformedResources.size} resource(s).$cachedMessage"
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

    data class ResourceRequestKey(
        val runId: String,
        val resourceType: ResourceType,
        val tenant: Tenant,
        val resourceId: String
    ) {
        // This is to ensure we have a consistent ID to base indexing off of.
        private val unlocalizedResourceId = resourceId.unlocalize(tenant)

        override fun toString(): String = "$runId:$resourceType:${tenant.mnemonic}:$unlocalizedResourceId"
    }

    /**
     * Since most load channels are subscribed to different topics, this is a wrapper class so the main loop of the channel
     * doesn't have to worry about how to handle different events and instead can always just call [loadResources]
     */
    abstract class ResourceLoadRequest<T : Resource<T>> {
        abstract val sourceEvent: Any
        abstract val dataTrigger: DataTrigger
        abstract val fhirService: FHIRService<T>
        abstract val tenant: Tenant
        abstract val metadata: Metadata
        abstract val requestKeys: List<ResourceRequestKey>

        abstract fun loadResources(requestKeys: List<ResourceRequestKey>): List<T>
    }

    /**
     * Wrapper for [InteropResourceLoadV1] events, so the implementer can focus on writing the [loadResources]
     */
    abstract class LoadEventResourceLoadRequest<T : Resource<T>>(
        final override val sourceEvent: InteropResourceLoadV1,
        final override val tenant: Tenant
    ) :
        ResourceLoadRequest<T>() {
        final override val dataTrigger: DataTrigger = when (sourceEvent.dataTrigger) {
            InteropResourceLoadV1.DataTrigger.adhoc -> DataTrigger.AD_HOC
            InteropResourceLoadV1.DataTrigger.nightly -> DataTrigger.NIGHTLY
            else -> {
                // backfill
                throw IllegalStateException("Received a data trigger which cannot be transformed to a known value")
            }
        }

        final override val metadata: Metadata = sourceEvent.metadata
        final override val requestKeys: List<ResourceRequestKey> =
            listOf(ResourceRequestKey(metadata.runId, sourceEvent.resourceType, tenant, sourceEvent.resourceFHIRId))

        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<T> {
            // Since a load can only request a single resource, the request key does not actually matter here
            return listOf(fhirService.getByID(tenant, sourceEvent.resourceFHIRId.unlocalize(tenant)))
        }
    }

    /**
     * Wrapper for [InteropResourcePublishV1] events, so the implementer can focus on writing the [loadResources].
     * If you are only loading by the FHIR resource's ID, use [IdBasedPublishEventResourceLoadRequest].
     *
     * [T] is the resource that should be returned by this load. [S] is the resource that is used as the
     * source event for the load request.
     *
     * For instance, if an Appointment is being used to load Locations, [T] would be [com.projectronin.interop.fhir.r4.resource.Location]
     * and [S] would be [com.projectronin.interop.fhir.r4.resource.Appointment]
     */
    abstract class PublishEventResourceLoadRequest<T : Resource<T>, S : Resource<S>>(
        final override val sourceEvent: InteropResourcePublishV1
    ) :
        ResourceLoadRequest<T>() {
        final override val dataTrigger: DataTrigger = when (sourceEvent.dataTrigger) {
            InteropResourcePublishV1.DataTrigger.adhoc -> DataTrigger.AD_HOC
            InteropResourcePublishV1.DataTrigger.nightly -> DataTrigger.NIGHTLY
            else -> {
                // backfill
                throw IllegalStateException("Received a data trigger which cannot be transformed to a known value")
            }
        }

        final override val metadata: Metadata = sourceEvent.metadata

        abstract val sourceResource: S
    }

    /**
     * Wrapper for [InteropResourcePublishV1] events that always use the FHIR resource's ID to load resources.
     *
     * [T] is the resource that should be returned by this load. [S] is the resource that is used as the
     * source event for the load request.
     *
     * For instance, if an Appointment is being used to load Locations, [T] would be [com.projectronin.interop.fhir.r4.resource.Location]
     * and [S] would be [com.projectronin.interop.fhir.r4.resource.Appointment]
     */
    abstract class IdBasedPublishEventResourceLoadRequest<T : Resource<T>, S : Resource<S>>(
        sourceEvent: InteropResourcePublishV1,
        final override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<T, S>(sourceEvent) {
        private val resourceType: ResourceType = sourceEvent.resourceType

        // The default case assumes we are using the published resource ID as our key
        override val requestKeys: List<ResourceRequestKey> by lazy {
            listOf(ResourceRequestKey(metadata.runId, resourceType, tenant, sourceResource.id!!.value!!))
        }

        abstract fun loadResources(): List<T>

        override fun loadResources(requestKeys: List<ResourceRequestKey>): List<T> = loadResources()
    }
}
