package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.channel.destinations.queue.PractitionerQueueWriter
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class PractitionerQueue(
    tenantService: TenantService,
    transformManager: TransformManager,
    practitionerQueueWriter: PractitionerQueueWriter,
    queueService: QueueService,
    private val roninPractitioner: RoninPractitioner
) : BaseQueue<Practitioner>(tenantService, transformManager, practitionerQueueWriter, queueService) {
    companion object : ChannelFactory<PractitionerQueue>()

    override val rootName = "PractitionerQueue"
    override val resourceType = ResourceType.PRACTITIONER

    override fun deserializeAndTransform(string: String, tenant: Tenant): Practitioner {
        val practitioner = JacksonUtil.readJsonObject(string, Practitioner::class)
        return transformManager.transformResource(practitioner, roninPractitioner, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Practitioner for tenant ${tenant.mnemonic}")
    }
}
