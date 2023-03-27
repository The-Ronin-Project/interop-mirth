package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.mirth.channel.base.KafkaQueue
import com.projectronin.interop.mirth.channel.destinations.queue.ConditionTenantlessQueueWriter
import com.projectronin.interop.mirth.spring.SpringUtil
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
    companion object {
        fun create() = SpringUtil.applicationContext.getBean(KafkaConditionQueue::class.java)
    }

    override val limit = 2 // this is used as a hack to give the channel a unique group ID
    override val rootName = "KafkaConditionQueue"
    override val resourceType = ResourceType.CONDITION

    override fun deserializeAndTransform(string: String, tenant: Tenant): Condition {
        val condition = JacksonUtil.readJsonObject(string, Condition::class)
        return transformManager.transformResource(condition, roninCondition, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Condition for tenant ${tenant.mnemonic}")
    }
}
