package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
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
    override val channelGroupId = "interop-mirth-practitioner_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Appointment)
    override val resource = ResourceType.Practitioner

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(PractitionerLoad::class.java)
    }
}
