package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.ServiceRequestPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class ServiceRequestLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: ServiceRequestPublish,
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "ServiceRequestLoad"
    override val channelGroupId = "interop-mirth-service-request_group"
    override val publishedResourcesSubscriptions =
        listOf(
            ResourceType.Patient,
            ResourceType.MedicationRequest,
            ResourceType.Encounter,
            ResourceType.Appointment,
            ResourceType.DiagnosticReport,
            ResourceType.MedicationStatement,
            ResourceType.Observation,
            ResourceType.Procedure,
        )
    override val resource = ResourceType.ServiceRequest
    override val publishEventOverrideResources = listOf(ResourceType.Patient)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ServiceRequestLoad::class.java)
    }
}
