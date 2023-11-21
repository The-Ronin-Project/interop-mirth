package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.LocationPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class LocationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: LocationPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "LocationLoad"
    override val channelGroupId = "interop-mirth-location_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Appointment, ResourceType.Encounter)
    override val resource = ResourceType.Location

    companion object : LoadChannelConfiguration<LocationLoad>() {
        override val channelClass = LocationLoad::class
        override val id = "1cf7c2b1-b8b4-4afb-9fbf-19e188532ac9"
        override val description = "Reads Kafka events and finds appropriate locations based on those events"
    }
}
