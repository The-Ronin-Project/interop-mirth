package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.publishers.PublishService
import org.springframework.stereotype.Component

@Component
class ConditionTenantlessQueueWriter(publishService: PublishService) :
    TenantlessQueueWriter<Condition>(publishService, Condition::class)
