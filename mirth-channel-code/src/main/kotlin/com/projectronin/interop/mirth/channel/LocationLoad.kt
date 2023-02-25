package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.LocationPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class LocationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    defaultPublisher: LocationPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "LocationLoad"
    override val channelGroupId = "interop-mirth-location"
    override val publishedResourcesSubscriptions = listOf(ResourceType.APPOINTMENT)
    override val resource = ResourceType.LOCATION

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(LocationLoad::class.java)
    }
}
