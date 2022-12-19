package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.channel.destinations.queue.AppointmentQueueWriter
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class AppointmentQueue(
    tenantService: TenantService,
    transformManager: TransformManager,
    appointmentQueueWriter: AppointmentQueueWriter,
    queueService: QueueService,
    private val roninAppointment: RoninAppointment
) :
    BaseQueue<Appointment>(tenantService, transformManager, appointmentQueueWriter, queueService) {
    companion object : ChannelFactory<AppointmentQueue>()

    override val resourceType = ResourceType.APPOINTMENT
    override val rootName = "AppointmentQueue"

    override fun deserializeAndTransform(string: String, tenant: Tenant): Appointment {
        val appointment = JacksonUtil.readJsonObject(string, Appointment::class)
        return transformManager.transformResource(appointment, roninAppointment, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Appointment for tenant ${tenant.mnemonic}")
    }
}
