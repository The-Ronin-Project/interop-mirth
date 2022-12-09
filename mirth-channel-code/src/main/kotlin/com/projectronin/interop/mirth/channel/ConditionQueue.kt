package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant

class ConditionQueue(serviceFactory: ServiceFactory) :
    BaseQueue<Condition>(serviceFactory, Condition::class) {
    companion object : ChannelFactory<ConditionQueue>()

    override val rootName = "ConditionQueue"
    override val resourceType = ResourceType.CONDITION

    override fun deserializeAndTransform(string: String, tenant: Tenant): Condition {
        val condition = JacksonUtil.readJsonObject(string, Condition::class)
        return serviceFactory.transformManager().transformResource(condition, RoninConditions, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Condition for tenant ${tenant.mnemonic}")
    }
}
