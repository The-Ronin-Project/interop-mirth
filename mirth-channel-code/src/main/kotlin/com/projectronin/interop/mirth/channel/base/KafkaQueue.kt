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
) : TenantlessSourceService() {
    protected val publishService = "publish"
    open val limit = 20
    abstract val resourceType: ResourceType

    override val destinations by lazy {
        mapOf(
            publishService to queueWriter
        )
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        return queueService.dequeueApiMessages("", resourceType, limit).map {
            MirthMessage(it.text, mapOf(MirthKey.TENANT_MNEMONIC.code to it.tenant))
        }
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic) ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val transformed = deserializeAndTransform(msg, tenant)
        return MirthMessage(
            message = JacksonManager.objectMapper.writeValueAsString(transformed),
            dataMap = mapOf(
                MirthKey.FHIR_ID.code to (transformed.id?.value ?: ""),
                MirthKey.TENANT_MNEMONIC.code to tenantMnemonic
            )
        )
    }

    /**
     * Implementers should take a string from off the queue and then turn them into a DomainResource
     */
    abstract fun deserializeAndTransform(string: String, tenant: Tenant): K
}
