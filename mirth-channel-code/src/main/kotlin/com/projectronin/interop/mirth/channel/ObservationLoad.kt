package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.ObservationPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class ObservationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: ObservationPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "ObservationLoad"
    override val channelGroupId = "interop-mirth-observation_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient, ResourceType.Condition)
    override val resource = ResourceType.Observation
    override val maxBackfillDays = 30

    companion object : LoadChannelConfiguration<ObservationLoad>() {
        override val channelClass = ObservationLoad::class
        override val id = "97b6f1db-9bd9-4251-9d04-d25871723b14"
        override val description = "Reads Kafka events and finds appropriate observations based on those events"
    }
}
