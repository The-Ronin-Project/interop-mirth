package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.stereotype.Component

@Component
class PractitionerQueueWriter(
    tenantService: TenantService,
    transformManager: TransformManager,
    publishService: PublishService
) : QueueWriter<Practitioner>(tenantService, transformManager, publishService, Practitioner::class)
