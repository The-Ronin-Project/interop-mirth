package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.LoadChannelConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.DocumentReferencePublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import org.springframework.stereotype.Component

@Component
class DocumentReferenceLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: DocumentReferencePublish
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "DocumentReferenceLoad"
    override val channelGroupId = "interop-mirth-document_group"
    override val publishedResourcesSubscriptions = listOf(
        ResourceType.Patient,
        ResourceType.DocumentReference
    )
    override val resource = ResourceType.DocumentReference

    companion object : LoadChannelConfiguration<DocumentReferenceLoad>() {
        override val channelClass = DocumentReferenceLoad::class
        override val id = "d8dcc430-8e99-49af-a8e5-13160ef09cc3"
        override val description = "Reads Kafka events and finds appropriate document references based on those events"
    }
}
