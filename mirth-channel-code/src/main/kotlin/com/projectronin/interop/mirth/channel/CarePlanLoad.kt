package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.CarePlanPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
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

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(CarePlanLoad::class.java)
    }
}
