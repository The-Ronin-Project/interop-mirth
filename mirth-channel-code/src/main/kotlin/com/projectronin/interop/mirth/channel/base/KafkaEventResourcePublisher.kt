package com.projectronin.interop.mirth.channel.base

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
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
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant

abstract class KafkaEventResourcePublisher<T : Resource<T>>(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val transformManager: TransformManager,
    private val publishService: PublishService,
    private val profileTransformer: BaseProfile<T>
) : TenantlessDestinationService() {

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
        val resources =
            runCatching {
                resourceLoadRequest.loadResources()
            }.fold(
                onSuccess = { it },
                onFailure = {
                    logger.error(it) { "Failed to retrieve resources from EHR" }
                    return MirthResponse(
                        status = MirthResponseStatus.ERROR,
                        detailedMessage = it.message,
                        message = "Failed EHR Call"
                    )
                }
            )

        if (resources.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                detailedMessage = "No resources retrieved from EHR",
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
        // if some of our resources failed to transform, we should alert, but publish the ones that worked
        if (transformedResources.size != resources.size) {
            logger.error {
                "Received ${resources.size} resources from EHR but only transformed ${transformedResources.size} " +
                    "for tenant: $tenantMnemonic.\n" +
                    "Resources received: ${resources.map { it.id?.value }} \n" +
                    "Resources transformed: ${transformedResources.map { it.id?.value }}"
            }
        }

        if (!publishService.publishFHIRResources(
                tenantMnemonic,
                transformedResources,
                resourceLoadRequest.metadata,
                resourceLoadRequest.dataTrigger
            )
        ) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = transformedResources.truncateList(),
                message = "Failed to publish ${transformedResources.size} resource(s)"
            )
        }

        return MirthResponse(
            status = MirthResponseStatus.SENT,
            detailedMessage = transformedResources.truncateList(),
            message = "Published ${transformedResources.size} resource(s)"
        )
    }

    fun List<Resource<*>>.truncateList(): String {
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
     * Since most load channels are subscribed to different topics, this is a wrapper class so the main loop of the channel
     * doesn't have to worry about how to handle different events and instead can always just call [loadResources]
     */
    abstract class ResourceLoadRequest<T : Resource<T>> {
        abstract val sourceEvent: Any
        abstract val dataTrigger: DataTrigger
        abstract val fhirService: FHIRService<T>
        abstract val tenant: Tenant
        abstract val metadata: Metadata
        abstract fun loadResources(): List<T>
    }

    /**
     * wrapper for [InteropResourceLoadV1] events, so the implementer can focus on writing the [loadResources]
     */
    abstract class LoadEventResourceLoadRequest<T : Resource<T>>(final override val sourceEvent: InteropResourceLoadV1) :
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

        override fun loadResources(): List<T> {
            return listOf(fhirService.getByID(tenant, sourceEvent.resourceFHIRId))
        }
    }

    /**
     * wrapper for [InteropResourcePublishV1] events, so the implementer can focus on writing the [loadResources]
     */
    abstract class PublishEventResourceLoadRequest<T : Resource<T>>(final override val sourceEvent: InteropResourcePublishV1) :
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
    }
}
