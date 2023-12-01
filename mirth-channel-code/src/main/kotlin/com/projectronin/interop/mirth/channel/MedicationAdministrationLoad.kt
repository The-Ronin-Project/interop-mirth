package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.MedicationAdministrationPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class MedicationAdministrationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: MedicationAdministrationPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "MedicationAdministrationLoad"
    override val channelGroupId = "interop-mirth-medication-administration_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient, ResourceType.MedicationRequest)
    override val resource = ResourceType.MedicationAdministration
    override val maxBackfillDays = 30

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(MedicationAdministrationLoad::class.java)
    }
}