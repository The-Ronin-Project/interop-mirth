package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.MedicationRequestPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class MedicationRequestLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    defaultPublisher: MedicationRequestPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "MedicationRequestLoad"
    override val channelGroupId = "interop-mirth-medication-request_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.PATIENT)
    override val resource = ResourceType.MEDICATION_REQUEST

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(MedicationRequestLoad::class.java)
    }
}
