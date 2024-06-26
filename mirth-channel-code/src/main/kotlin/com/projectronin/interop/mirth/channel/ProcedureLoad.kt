package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.base.kafka.completeness.KafkaDagPublisher
import com.projectronin.interop.mirth.channel.destinations.ProcedurePublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class ProcedureLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: ProcedurePublish,
    kafkaDagPublisher: KafkaDagPublisher,
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher, kafkaDagPublisher) {
    override val rootName = "ProcedureLoad"
    override val channelGroupId = "interop-mirth-procedure_group"
    override val publishedResourcesSubscriptions =
        listOf(
            ResourceType.Patient,
            ResourceType.Appointment,
            ResourceType.Encounter,
            ResourceType.MedicationStatement,
            ResourceType.Observation,
            ResourceType.Procedure,
        )
    override val resource = ResourceType.Procedure
    override val publishEventOverrideResources = listOf(ResourceType.Patient)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ProcedureLoad::class.java)
    }
}
