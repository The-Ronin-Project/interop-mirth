package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.stereotype.Component

@Component
class ConditionQueueWriter(
    tenantService: TenantService,
    transformManager: TransformManager,
    publishService: PublishService
) : QueueWriter<Condition>(tenantService, transformManager, publishService, Condition::class)
