package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.AppointmentPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class AppointmentLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: AppointmentPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "AppointmentLoad"
    override val channelGroupId = "interop-mirth-appointment_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient)
    override val resource = ResourceType.Appointment
    override val maxBackfillDays = 30

    companion object : LoadChannelConfiguration<AppointmentLoad>() {
        override val channelClass = AppointmentLoad::class
        override val id = "160b2076-2763-4772-b4ef-0ef1c78a676f"
        override val description = "Reads Kafka events and finds appropriate appointment based on those events"
    }
}
