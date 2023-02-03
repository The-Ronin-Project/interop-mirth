package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.ConditionPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class ConditionLoad(
    private val kafkaPublishService: KafkaPublishService,
    private val kafkaLoadService: KafkaLoadService,
    conditionPublish: ConditionPublish
) : TenantlessSourceService() {
    override val rootName = "PatientLoad"
    override val destinations = mapOf("publish" to conditionPublish)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ConditionLoad::class.java)
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        val nightlyEvents =
            kafkaPublishService.retrievePublishEvents(resourceType = ResourceType.PATIENT, DataTrigger.NIGHTLY)
        if (nightlyEvents.isNotEmpty()) {
            return nightlyEvents.map {
                MirthMessage(
                    JacksonUtil.writeJsonValue(it),
                    mapOf(
                        MirthKey.TENANT_MNEMONIC.code to it.tenantId,
                        MirthKey.KAFKA_EVENT.code to it::class.simpleName!!
                    )
                )
            }
        }
        val loadEvents = kafkaLoadService.retrieveLoadEvents(resourceType = ResourceType.CONDITION)
        if (loadEvents.isNotEmpty()) {
            return loadEvents.map {
                MirthMessage(
                    JacksonUtil.writeJsonValue(it),
                    mapOf(
                        MirthKey.TENANT_MNEMONIC.code to it.tenantId,
                        MirthKey.KAFKA_EVENT.code to it::class.simpleName!!
                    )
                )
            }
        }

        return kafkaPublishService.retrievePublishEvents(resourceType = ResourceType.PATIENT, DataTrigger.AD_HOC).map {
            MirthMessage(
                JacksonUtil.writeJsonValue(it),
                mapOf(
                    MirthKey.TENANT_MNEMONIC.code to it.tenantId,
                    MirthKey.KAFKA_EVENT.code to it::class.simpleName!!
                )
            )
        }
    }
}
