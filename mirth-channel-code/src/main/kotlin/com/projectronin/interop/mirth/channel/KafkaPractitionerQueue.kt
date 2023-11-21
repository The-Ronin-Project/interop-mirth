package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.fhir.ronin.transform.TransformResponse
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaQueue
import com.projectronin.interop.mirth.channel.destinations.queue.PractitionerTenantlessQueueWriter
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
    companion object : ChannelConfiguration<KafkaPractitionerQueue>() {
        override val channelClass = KafkaPractitionerQueue::class
        override val id = "1eaf4bd8-7f32-44d7-8697-0b758e7c581e"
        override val description =
            "Reads Practitioners off the Kafka Queue. Transforms and publishes them to the clinical data store."
        override val metadataColumns: Map<String, String> = mapOf(
            "TENANT" to "tenantMnemonic",
            "FHIRID" to "fhirID"
        )
    }

    override val limit = 4 // this is used as a hack to give the channel a unique group ID
    override val rootName = "KafkaPractitionerQueue"
    override val resourceType = ResourceType.PRACTITIONER

    override fun deserializeAndTransform(string: String, tenant: Tenant): TransformResponse<Practitioner> {
        val practitioner = JacksonUtil.readJsonObject(string, Practitioner::class)
        return transformManager.transformResource(practitioner, roninPractitioner, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Practitioner for tenant ${tenant.mnemonic}")
    }
}
