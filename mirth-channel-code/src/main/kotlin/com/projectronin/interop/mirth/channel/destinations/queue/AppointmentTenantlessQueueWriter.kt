package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.publishers.PublishService
import org.springframework.stereotype.Component

@Component
class AppointmentTenantlessQueueWriter(publishService: PublishService) :
    TenantlessQueueWriter<Appointment>(publishService, Appointment::class)
