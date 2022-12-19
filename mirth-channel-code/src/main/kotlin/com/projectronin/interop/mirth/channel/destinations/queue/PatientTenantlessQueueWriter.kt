package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.publishers.PublishService
import org.springframework.stereotype.Component

@Component
class PatientTenantlessQueueWriter(publishService: PublishService) :
    TenantlessQueueWriter<Patient>(publishService, Patient::class)
