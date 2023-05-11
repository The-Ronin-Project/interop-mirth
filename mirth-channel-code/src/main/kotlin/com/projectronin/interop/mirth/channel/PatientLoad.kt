package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.PatientPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class PatientLoad(
    kafkaLoadService: KafkaLoadService,
    kafkaPublishService: KafkaPublishService,
    defaultPublisher: PatientPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "PatientLoad"
    override val channelGroupId = "interop-mirth-patient_group"
    override val publishedResourcesSubscriptions =
        emptyList<ResourceType>() // patient is not dependent on any resources
    override val resource = ResourceType.Patient

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(PatientLoad::class.java)
    }
}
