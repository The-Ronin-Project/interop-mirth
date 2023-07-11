package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.mirth.channel.base.kafka.KafkaQueue
import com.projectronin.interop.mirth.channel.destinations.queue.PractitionerTenantlessQueueWriter
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

/**
 * This channel reads off the Kafka Queue for Practitioner messages, transforms them into RoninPractitioner and publishes them
 */
@Component
class KafkaPractitionerQueue(
    tenantService: TenantService,
    queueService: KafkaQueueService,
    practitionerQueueWriter: PractitionerTenantlessQueueWriter,
    private val transformManager: TransformManager,
    private val roninPractitioner: RoninPractitioner
) :
    KafkaQueue<Practitioner>(tenantService, queueService, practitionerQueueWriter) {
    companion object {
        fun create() = SpringUtil.applicationContext.getBean(KafkaPractitionerQueue::class.java)
    }

    override val limit = 4 // this is used as a hack to give the channel a unique group ID
    override val rootName = "KafkaPractitionerQueue"
    override val resourceType = ResourceType.PRACTITIONER

    override fun deserializeAndTransform(string: String, tenant: Tenant): Practitioner {
        val practitioner = JacksonUtil.readJsonObject(string, Practitioner::class)
        return transformManager.transformResource(practitioner, roninPractitioner, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Practitioner for tenant ${tenant.mnemonic}")
    }
}
