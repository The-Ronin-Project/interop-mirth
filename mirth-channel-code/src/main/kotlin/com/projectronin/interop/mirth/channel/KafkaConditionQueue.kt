package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.fhir.ronin.transform.TransformResponse
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaQueue
import com.projectronin.interop.mirth.channel.destinations.queue.ConditionTenantlessQueueWriter
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

/**
 * This channel reads off the Kafka Queue for Condition messages, transforms them into RoninCondition and publishes them
 */
@Component
class KafkaConditionQueue(
    tenantService: TenantService,
    queueService: KafkaQueueService,
    conditionQueueWriter: ConditionTenantlessQueueWriter,
    private val transformManager: TransformManager,
    private val roninCondition: RoninConditions
) :
    KafkaQueue<Condition>(tenantService, queueService, conditionQueueWriter) {
    companion object : ChannelConfiguration<KafkaConditionQueue>() {
        override val channelClass = KafkaConditionQueue::class
        override val id = "765556ad-91c7-4ca8-9189-0c465393fe8e"
        override val description =
            "Reads Conditions off the Kafka Queue. Transforms and publishes them to the clinical data store."
        override val metadataColumns: Map<String, String> = mapOf(
            "TENANT" to "tenantMnemonic",
            "FHIRID" to "fhirID"
        )
    }

    override val limit = 2 // this is used as a hack to give the channel a unique group ID
    override val rootName = "KafkaConditionQueue"
    override val resourceType = ResourceType.CONDITION

    override fun deserializeAndTransform(string: String, tenant: Tenant): TransformResponse<Condition> {
        val condition = JacksonUtil.readJsonObject(string, Condition::class)
        return transformManager.transformResource(condition, roninCondition, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Condition for tenant ${tenant.mnemonic}")
    }
}
