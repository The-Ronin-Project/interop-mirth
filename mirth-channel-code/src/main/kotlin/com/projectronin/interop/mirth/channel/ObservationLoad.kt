package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.ObservationPublish
import com.projectronin.interop.mirth.channel.util.isForType
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class ObservationLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: ObservationPublish,
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "ObservationLoad"
    override val channelGroupId = "interop-mirth-observation_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient, ResourceType.Condition)
    override val resource = ResourceType.Observation
    override val maxBackfillDays = 30
    override val publishEventOverrideResources = listOf(ResourceType.Patient)

    override fun List<InteropResourcePublishV1>.filterUnnecessaryEvents(): List<InteropResourcePublishV1> =
        this.filter {
            when (it.resourceType) {
                ResourceType.Condition -> {
                    runCatching {
                        val sourceCondition = JacksonUtil.readJsonObject(it.resourceJson, Condition::class)
                        // we care about a condition if any stage has any assessment that references an observation
                        sourceCondition.stage.any { stage ->
                            stage.assessment.any { assessment ->
                                assessment.isForType(ResourceType.Observation)
                            }
                        }
                    }.getOrDefault(true)
                }
                else -> true
            }
        }

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ObservationLoad::class.java)
    }
}
