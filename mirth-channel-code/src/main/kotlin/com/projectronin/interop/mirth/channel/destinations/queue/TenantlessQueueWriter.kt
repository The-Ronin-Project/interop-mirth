package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.channel.util.getMetadata
import com.projectronin.interop.mirth.models.destination.DestinationConfiguration
import com.projectronin.interop.mirth.models.destination.JavaScriptDestinationConfiguration
import com.projectronin.interop.publishers.PublishService
import kotlin.reflect.KClass

/**
 *  This destination should be able to used for any publish action that's just taking a RoninDomainResource object
 *  and publishing it to the Ronin clinical data store
 */
abstract class TenantlessQueueWriter<T : DomainResource<T>>(
    private val publishService: PublishService,
    private val type: KClass<out T>
) : TenantlessDestinationService() {

    override fun getConfiguration(): DestinationConfiguration =
        JavaScriptDestinationConfiguration(name = "Publish ${type.simpleName}s")

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val resource = JacksonManager.objectMapper.readValue(msg, type.java)
        val resourceList = listOf(resource)
        val resourceType = type.simpleName
        if (!publishService.publishFHIRResources(tenantMnemonic, resourceList, getMetadata(sourceMap))) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = JacksonUtil.writeJsonValue(resourceList),
                message = "Failed to publish $resourceType(s)"
            )
        }
        return MirthResponse(
            status = MirthResponseStatus.SENT,
            detailedMessage = JacksonUtil.writeJsonValue(resourceList),
            message = "Published ${resourceList.size} $resourceType(s)",
            dataMap = emptyMap()
        )
    }
}
