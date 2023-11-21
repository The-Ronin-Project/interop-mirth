package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.PractitionerPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class PractitionerLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: PractitionerPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "PractitionerLoad"
    override val channelGroupId = "interop-mirth-practitioner_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Appointment)
    override val resource = ResourceType.Practitioner

    companion object : LoadChannelConfiguration<PractitionerLoad>() {
        override val channelClass = PractitionerLoad::class
        override val id = "41b32717-852d-40d9-1524-9db9bd1f6b79"
        override val description = "Reads Kafka events and finds appropriate practitioners based on those events"
    }
}
