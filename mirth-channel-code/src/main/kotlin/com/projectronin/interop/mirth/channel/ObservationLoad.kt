package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.ObservationPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class ObservationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    defaultPublisher: ObservationPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "ObservationLoad"
    override val channelGroupId = "interop-mirth-observation"
    override val publishedResourcesSubscriptions = listOf(ResourceType.PATIENT)
    override val resource = ResourceType.OBSERVATION

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ObservationLoad::class.java)
    }
}
