package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.publishers.PublishService
import org.springframework.stereotype.Component

@Component
class PractitionerTenantlessQueueWriter(publishService: PublishService) :
    TenantlessQueueWriter<Practitioner>(publishService, Practitioner::class)
