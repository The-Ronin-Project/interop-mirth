package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.mirth.channel.destinations.QueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.model.Tenant
import kotlin.reflect.KClass

/**
 *  This is a base for any channel that reads resources off the API Queue, transforms the objects
 *  and then publishes them to the clinical data store.
 */

abstract class BaseQueue<K : DomainResource<K>>(serviceFactory: ServiceFactory, publishClass: KClass<out K>) :
    ChannelService(serviceFactory) {
    protected val publishService = "publish"
    open val limit = 5
    abstract val resourceType: ResourceType
    override val destinations by lazy {
        mapOf(
            publishService to QueueWriter(rootName, serviceFactory, publishClass)
        )
    }

    override fun channelSourceReader(
        tenantMnemonic: String,
        serviceMap: Map<String, Any>
    ): List<MirthMessage> {
        val messages = serviceFactory.queueService().dequeueApiMessages(tenantMnemonic, resourceType, limit)
        return messages.map {
            MirthMessage(it.text)
        }
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val transformed = deserializeAndTransform(msg, tenant)
        return MirthMessage(
            message = JacksonManager.objectMapper.writeValueAsString(transformed),
            dataMap = mapOf(MirthKey.FHIR_ID.code to (transformed.id?.value ?: ""))
        )
    }

    /**
     * Implementers should take a string from off the queue and then turn them into a DomainResource
     */
    abstract fun deserializeAndTransform(string: String, tenant: Tenant): K
}
