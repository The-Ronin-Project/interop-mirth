package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.PatientPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class PatientLoad(
    kafkaLoadService: KafkaLoadService,
    kafkaPublishService: KafkaPublishService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: PatientPublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "PatientLoad"
    override val channelGroupId = "interop-mirth-patient_group"
    override val publishedResourcesSubscriptions =
        emptyList<ResourceType>() // patient is not dependent on any resources
    override val resource = ResourceType.Patient

    companion object : LoadChannelConfiguration<PatientLoad>() {
        override val channelClass = PatientLoad::class
        override val id = "735716c7-fbd9-4c9c-b7e2-f33153fda6c2"
        override val description = "Reads Kafka events and finds appropriate patients based on those events"
    }
}
