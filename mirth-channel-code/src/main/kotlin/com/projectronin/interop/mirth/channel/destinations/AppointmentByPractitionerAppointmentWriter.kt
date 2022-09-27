package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.connector.ServiceFactory

class AppointmentByPractitionerAppointmentWriter(rootName: String, serviceFactory: ServiceFactory) :
    DestinationService(rootName, serviceFactory) {
    /**
     * requires a patient fhir ID, retrieves a list of provider references for appointments,
     * transforms a list of appointments using those references
     */
    override fun channelDestinationTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        return deserializeAndTransformToMessage(tenantMnemonic, msg, Appointment::class, RoninAppointment)
    }

    /**
     * Publishes a set of transformed appointments
     */
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        return deserializeAndPublishList(tenantMnemonic, msg, Appointment::class)
    }
}
