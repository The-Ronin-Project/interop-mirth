package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.CarePlanPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class CarePlanLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: CarePlanPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "CarePlanLoad"
    override val channelGroupId = "interop-mirth-care_plan_group"
    override val publishedResourcesSubscriptions = listOf(
        ResourceType.Patient,
        ResourceType.CarePlan
    )
    override val resource = ResourceType.CarePlan
    override val maxBackfillDays = 30

    companion object : LoadChannelConfiguration<CarePlanLoad>() {
        override val channelClass = CarePlanLoad::class
        override val id = "9978bb41-45b2-4492-8377-df5371eb08d0"
        override val description = "Reads Kafka events and finds appropriate care plans based on those events"
    }
}
