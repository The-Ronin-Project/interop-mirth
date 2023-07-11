package com.projectronin.interop.mirth.channel.base.kafka

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.util.unlocalize
import com.projectronin.interop.tenant.config.model.Tenant

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

    /** Given that metadata should slightly change as the event processes through the channels
     this function lets the [ResourceLoadRequest] handle providing this information to the caller
     **/
    abstract fun getUpdatedMetadata(): Metadata

    /** this could easily be wrapped into getUpdatedMetadata, since it's only used on one subclass,
     but this is a useful way to get this information for use in the channel's columns
     **/
    abstract fun getSourceReference(): Metadata.UpstreamReference?
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
        listOf(
            ResourceRequestKey(
                metadata.runId,
                sourceEvent.resourceType,
                tenant,
                sourceEvent.resourceFHIRId
            )
        )

    // A load request is the start of the run, so its metadata doesn't need to change
    final override fun getUpdatedMetadata(): Metadata = this.metadata

    // and it doesn't have any upstream references
    final override fun getSourceReference(): Metadata.UpstreamReference? = null
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

    // add the new reference to the start of the list
    final override fun getUpdatedMetadata(): Metadata =
        metadata.copy(
            upstreamReferences = (this.metadata.upstreamReferences ?: emptyList()) +
                listOf(this.getSourceReference())
        )

    // source reference is just the id and type of the source event
    final override fun getSourceReference(): Metadata.UpstreamReference = Metadata.UpstreamReference(
        id = this.sourceResource.id!!.value!!,
        resourceType = this.sourceEvent.resourceType
    )

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
        listOf(
            ResourceRequestKey(
                metadata.runId,
                resourceType,
                tenant,
                sourceResource.id!!.value!!
            )
        )
    }

    abstract fun loadResources(): List<T>

    override fun loadResources(requestKeys: List<ResourceRequestKey>): List<T> = loadResources()
}

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
