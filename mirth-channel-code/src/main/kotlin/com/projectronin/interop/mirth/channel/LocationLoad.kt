package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.LocationPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
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

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(LocationLoad::class.java)
    }
}
