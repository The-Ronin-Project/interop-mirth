package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.destinations.queue.QueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant

/**
 *  This is a base for any channel that reads resources off the API Queue, transforms the objects
 *  and then publishes them to the clinical data store.
 */

abstract class BaseQueue<K : DomainResource<K>>(
    tenantService: TenantService,
    transformManager: TransformManager,
    queueWriter: QueueWriter<K>,
    private val queueService: QueueService
) :
    ChannelService(tenantService, transformManager) {
    protected val publishService = "publish"
    open val limit = 20
    abstract val resourceType: ResourceType
    override val destinations by lazy {
        mapOf(
            publishService to queueWriter
        )
    }

    override fun channelSourceReader(
        tenantMnemonic: String,
        serviceMap: Map<String, Any>
    ): List<MirthMessage> {
        val queueMessages = mutableListOf<ApiMessage>()
        while (true) {
            val currentMessages = queueService.dequeueApiMessages(tenantMnemonic, resourceType, limit)

            queueMessages.addAll(currentMessages)

            // If we read less than the limit, we're done.
            if (currentMessages.size < limit) {
                break
            }
        }

        return queueMessages.map {
            MirthMessage(it.text)
        }
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val tenant = getTenant(tenantMnemonic)
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
