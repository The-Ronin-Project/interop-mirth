package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.connector.ServiceFactory
import kotlin.reflect.KClass

/**
 *  This destination should be able to used for any publish action that's just taking a RoninDomainResource object
 *  and publishing it to the Ronin clinical data store
 */
class TenantlessQueueWriter<T : DomainResource<T>>(
    val rootName: String,
    val serviceFactory: ServiceFactory,
    private val type: KClass<out T>,
) {
    fun destinationWriter(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val resource = JacksonManager.objectMapper.readValue(msg, type.java)
        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code] as String
        val resourceList = listOf(resource)
        val resourceType = type.simpleName
        if (!serviceFactory.publishService().publishFHIRResources(tenantMnemonic, resourceList)) {
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

    fun destinationTransformer(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        return MirthMessage(msg)
    }
}
