package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.MedicationRequestPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class MedicationRequestLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: MedicationRequestPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "MedicationRequestLoad"
    override val channelGroupId = "interop-mirth-medication-request_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient)
    override val resource = ResourceType.MedicationRequest

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(MedicationRequestLoad::class.java)
    }
}
