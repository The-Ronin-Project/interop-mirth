package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.connector.ServiceFactory
import kotlin.reflect.KClass

/**
 *  This destination should be able to used for any publish action that's just taking a RoninDomainResource object
 *  and publishing it to the Ronin clinical data store
 */
class QueueWriter<T : DomainResource<T>>(
    rootName: String,
    serviceFactory: ServiceFactory,
    private val type: KClass<out T>,
) : DestinationService(rootName, serviceFactory) {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val resource = JacksonManager.objectMapper.readValue(msg, type.java)
        return publishResources(tenantMnemonic, listOf(resource), resource::class.simpleName)
    }
}
