package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant

class AppointmentQueue(serviceFactory: ServiceFactory) :
    BaseQueue<Appointment>(serviceFactory, Appointment::class) {
    companion object : ChannelFactory<AppointmentQueue>()

    override val resourceType = ResourceType.APPOINTMENT
    override val rootName = "AppointmentQueue"

    override fun deserializeAndTransform(string: String, tenant: Tenant): Appointment {
        val appointment = JacksonUtil.readJsonObject(string, Appointment::class)
        return appointment.transformTo(RoninAppointment, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Appointment for tenant ${tenant.mnemonic}")
    }
}
