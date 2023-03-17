package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.EncounterPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class EncounterLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    defaultPublisher: EncounterPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "EncounterLoad"
    override val channelGroupId = "interop-mirth-encounter_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.PATIENT)
    override val resource = ResourceType.ENCOUNTER

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(EncounterLoad::class.java)
    }
}
