package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.AppointmentPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class AppointmentLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: AppointmentPublish,
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "AppointmentLoad"
    override val channelGroupId = "interop-mirth-appointment_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient)
    override val resource = ResourceType.Appointment
    override val maxBackfillDays = 30

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(AppointmentLoad::class.java)
    }
}
