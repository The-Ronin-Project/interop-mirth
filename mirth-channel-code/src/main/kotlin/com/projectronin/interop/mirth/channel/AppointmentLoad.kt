package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.AppointmentPublish
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class AppointmentLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    defaultPublisher: AppointmentPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "AppointmentLoad"
    override val channelGroupId = "interop-mirth-appointment"
    override val publishedResourcesSubscriptions = listOf(ResourceType.PATIENT)
    override val resource = ResourceType.APPOINTMENT

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(AppointmentLoad::class.java)
    }
}
