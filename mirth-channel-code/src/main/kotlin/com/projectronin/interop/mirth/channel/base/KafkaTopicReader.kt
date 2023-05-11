package com.projectronin.interop.mirth.channel.base

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage

abstract class KafkaTopicReader(
    private val kafkaPublishService: KafkaPublishService,
    private val kafkaLoadService: KafkaLoadService,
    defaultPublisher: KafkaEventResourcePublisher<*>
) : TenantlessSourceService() {
    abstract val publishedResourcesSubscriptions: List<ResourceType>
    abstract val resource: ResourceType
    abstract val channelGroupId: String

    override val destinations: Map<String, KafkaEventResourcePublisher<*>> = mapOf("publish" to defaultPublisher)

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        val nightlyPublishedEvents = retrievePublishedEvents(DataTrigger.NIGHTLY)
        if (nightlyPublishedEvents.isNotEmpty()) {
            return nightlyPublishedEvents.toPublishMirthMessages()
        }

        val loadEvents = kafkaLoadService.retrieveLoadEvents(resourceType = resource, groupId = channelGroupId)
        if (loadEvents.isNotEmpty()) {
            return loadEvents.toLoadMirthMessages()
        }

        val adHocPublishEvents = retrievePublishedEvents(DataTrigger.AD_HOC)
        if (adHocPublishEvents.isNotEmpty()) {
            return adHocPublishEvents.toPublishMirthMessages()
        }

        return emptyList()
    }

    // Grab the first set of published events from the subscribed resources
    private fun retrievePublishedEvents(dataTrigger: DataTrigger): List<InteropResourcePublishV1> {
        // using sequences forces us to complete the .map and .firstOrNull
        // for each resourceType before moving to the next one
        return publishedResourcesSubscriptions
            .asSequence()
            .map {
                kafkaPublishService.retrievePublishEvents(
                    resourceType = it,
                    dataTrigger = dataTrigger,
                    groupId = channelGroupId
                )
            }
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList() // this is outside the sequence loop, so it only happens once all events have been drained
    }

    private fun List<InteropResourcePublishV1>.toPublishMirthMessages(): List<MirthMessage> {
        return this.map {
            MirthMessage(
                JacksonUtil.writeJsonValue(it),
                mapOf(
                    MirthKey.TENANT_MNEMONIC.code to it.tenantId,
                    MirthKey.KAFKA_EVENT.code to it::class.simpleName!!
                )
            )
        }
    }

    private fun List<InteropResourceLoadV1>.toLoadMirthMessages(): List<MirthMessage> {
        return this.map {
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
