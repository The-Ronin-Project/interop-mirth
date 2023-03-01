package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.PractitionerPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class PractitionerLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    defaultPublisher: PractitionerPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "PractitionerLoad"
    override val channelGroupId = "interop-mirth-practitioner"
    override val publishedResourcesSubscriptions = listOf(ResourceType.APPOINTMENT)
    override val resource = ResourceType.PRACTITIONER

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(PractitionerLoad::class.java)
    }
}
