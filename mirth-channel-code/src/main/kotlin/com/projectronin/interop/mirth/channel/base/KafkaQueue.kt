package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.mirth.channel.destinations.queue.TenantlessQueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant

/**
 *  This is a base for any channel that reads resources off the API Queue, transforms the objects
 *  and then publishes them to the clinical data store.
 */

abstract class KafkaQueue<K : DomainResource<K>>(
    private val tenantService: TenantService,
    private val queueService: KafkaQueueService,
    queueWriter: TenantlessQueueWriter<K>
) {
    protected val publishService = "publish"
    open val limit = 20
    abstract val resourceType: ResourceType
    abstract val rootName: String
    val destinations by lazy {
        mapOf(
            publishService to queueWriter
        )
    }

    fun onDeploy(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        require(rootName.length <= 31) { "Channel root name length is over the limit of 31" }
        require(deployedChannelName.length <= 40) { "Deployed channel name length is over the limit of 40" }
        return serviceMap
    }

    fun sourceReader(
        deployedChannelName: String,
        serviceMap: Map<String, Any>
    ): List<MirthMessage> {
        return queueService.dequeueApiMessages("", resourceType, limit).map {
            MirthMessage(it.text, mapOf(MirthKey.TENANT_MNEMONIC.code to it.tenant))
        }
    }

    fun sourceTransformer(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val tenantId = sourceMap[MirthKey.TENANT_MNEMONIC.code] as String
        val tenant =
            tenantService.getTenantForMnemonic(tenantId) ?: throw IllegalArgumentException("Unknown tenant: $tenantId")
        val transformed = deserializeAndTransform(msg, tenant)
        return MirthMessage(
            message = JacksonManager.objectMapper.writeValueAsString(transformed),
            dataMap = mapOf(
                MirthKey.FHIR_ID.code to (transformed.id?.value ?: ""),
                MirthKey.TENANT_MNEMONIC.code to tenantId
            )
        )
    }

    /**
     * Implementers should take a string from off the queue and then turn them into a DomainResource
     */
    abstract fun deserializeAndTransform(string: String, tenant: Tenant): K
}
