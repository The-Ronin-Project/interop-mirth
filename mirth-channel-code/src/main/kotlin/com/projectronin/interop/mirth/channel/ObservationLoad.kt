package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.ObservationPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class ObservationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: ObservationPublish,
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "ObservationLoad"
    override val channelGroupId = "interop-mirth-observation_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient, ResourceType.Condition)
    override val resource = ResourceType.Observation
    override val maxBackfillDays = 30
    override val publishEventOverrideResources = listOf(ResourceType.Patient)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ObservationLoad::class.java)
    }
}
