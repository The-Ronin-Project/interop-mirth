package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant

class PractitionerQueue(serviceFactory: ServiceFactory) : BaseQueue<Practitioner>(serviceFactory, Practitioner::class) {
    companion object : ChannelFactory<PractitionerQueue>()

    override val rootName = "PractitionerQueue"
    override val resourceType = ResourceType.PRACTITIONER

    override fun deserializeAndTransform(string: String, tenant: Tenant): Practitioner {
        val practitioner = JacksonUtil.readJsonObject(string, Practitioner::class)
        return practitioner.transformTo(RoninPractitioner, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Practitioner for tenant ${tenant.mnemonic}")
    }
}
