package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.channel.destinations.queue.ConditionQueueWriter
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class ConditionQueue(
    tenantService: TenantService,
    transformManager: TransformManager,
    conditionQueueWriter: ConditionQueueWriter,
    queueService: QueueService,
    private val roninConditions: RoninConditions
) :
    BaseQueue<Condition>(tenantService, transformManager, conditionQueueWriter, queueService) {
    companion object : ChannelFactory<ConditionQueue>()

    override val rootName = "ConditionQueue"
    override val resourceType = ResourceType.CONDITION

    override fun deserializeAndTransform(string: String, tenant: Tenant): Condition {
        val condition = JacksonUtil.readJsonObject(string, Condition::class)
        return transformManager.transformResource(condition, roninConditions, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Condition for tenant ${tenant.mnemonic}")
    }
}
