package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.MedicationPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class MedicationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: MedicationPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "MedicationLoad"
    override val channelGroupId = "interop-mirth-medication_group"
    override val publishedResourcesSubscriptions = listOf(
        ResourceType.Medication,
        ResourceType.MedicationRequest,
        ResourceType.MedicationStatement,
        ResourceType.MedicationAdministration
    )
    override val resource = ResourceType.Medication

    companion object : LoadChannelConfiguration<MedicationLoad>() {
        override val channelClass = MedicationLoad::class
        override val id = "ff6a5085-470d-4b90-bbbb-641f50b92577"
        override val description = "Reads Kafka events and finds appropriate medications based on those events"
    }
}
