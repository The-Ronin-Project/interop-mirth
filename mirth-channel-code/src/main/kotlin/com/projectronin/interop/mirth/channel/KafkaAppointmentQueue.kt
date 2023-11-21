package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.fhir.ronin.transform.TransformResponse
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaQueue
import com.projectronin.interop.mirth.channel.destinations.queue.AppointmentTenantlessQueueWriter
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

/**
 * This channel reads off the Kafka Queue for Appointment messages, transforms them into RoninAppointment and publishes them
 */
@Component
class KafkaAppointmentQueue(
    tenantService: TenantService,
    queueService: KafkaQueueService,
    appointmentQueueWriter: AppointmentTenantlessQueueWriter,
    private val transformManager: TransformManager,
    private val roninAppointment: RoninAppointment
) :
    KafkaQueue<Appointment>(tenantService, queueService, appointmentQueueWriter) {
    companion object : ChannelConfiguration<KafkaAppointmentQueue>() {
        override val channelClass = KafkaAppointmentQueue::class
        override val id = "f2956ab5-9c8c-4dbf-b789-12726f91454e"
        override val description =
            "Reads Appointments off the Kafka Queue. Transforms and publishes them to the clinical data store."
        override val metadataColumns: Map<String, String> = mapOf(
            "TENANT" to "tenantMnemonic",
            "FHIRID" to "fhirID"
        )
    }

    override val limit = 1 // this is used as a hack to give the channel a unique group ID
    override val rootName = "KafkaAppointmentQueue"
    override val resourceType = ResourceType.APPOINTMENT

    override fun deserializeAndTransform(string: String, tenant: Tenant): TransformResponse<Appointment> {
        val condition = JacksonUtil.readJsonObject(string, Appointment::class)
        return transformManager.transformResource(condition, roninAppointment, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Appointment for tenant ${tenant.mnemonic}")
    }
}
