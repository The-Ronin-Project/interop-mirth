package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.RequestGroupPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class RequestGroupLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    defaultPublisher: RequestGroupPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "RequestGroupLoad"
    override val channelGroupId = "interop-mirth-request_group_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.CarePlan)
    override val resource = ResourceType.RequestGroup

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(RequestGroupLoad::class.java)
    }
}
