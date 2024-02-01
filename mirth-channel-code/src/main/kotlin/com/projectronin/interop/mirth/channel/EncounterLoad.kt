package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.EncounterPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class EncounterLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: EncounterPublish,
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "EncounterLoad"
    override val channelGroupId = "interop-mirth-encounter_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient)
    override val resource = ResourceType.Encounter
    override val publishEventOverrideResources = listOf(ResourceType.Patient)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(EncounterLoad::class.java)
    }
}
