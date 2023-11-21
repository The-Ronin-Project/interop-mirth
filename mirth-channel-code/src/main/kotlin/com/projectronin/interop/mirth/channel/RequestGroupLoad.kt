package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.RequestGroupPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class RequestGroupLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: RequestGroupPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "RequestGroupLoad"
    override val channelGroupId = "interop-mirth-request_group_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.CarePlan)
    override val resource = ResourceType.RequestGroup

    companion object : LoadChannelConfiguration<RequestGroupLoad>() {
        override val channelClass = RequestGroupLoad::class
        override val id = "85fadd5c-7d27-432f-96a5-74caeeb53fc0"
        override val description = "Reads Kafka events and finds appropriate request groups based on those events"
    }
}
