package com.projectronin.interop.mirth.channel.base.kafka

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.util.filterBlockedLoadEvents
import com.projectronin.interop.mirth.channel.util.filterBlockedPublishedEvents
import com.projectronin.interop.mirth.service.TenantConfigurationService

abstract class KafkaTopicReader(
    private val kafkaPublishService: KafkaPublishService,
    private val kafkaLoadService: KafkaLoadService,
    defaultPublisher: KafkaEventResourcePublisher<*>
) : TenantlessSourceService() {
    abstract val publishedResourcesSubscriptions: List<ResourceType>
    abstract val resource: ResourceType
    abstract val channelGroupId: String
    abstract val tenantConfigService: TenantConfigurationService

    override val destinations: Map<String, KafkaEventResourcePublisher<*>> = mapOf("publish" to defaultPublisher)

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        // wrapping retrievePublishedEvents with function to filter out blocked resources
        val nightlyPublishedEvents = filterBlockedPublishedEvents(resource, retrievePublishedEvents(DataTrigger.NIGHTLY), tenantConfigService)
        if (nightlyPublishedEvents.isNotEmpty()) {
            return nightlyPublishedEvents.toPublishMirthMessages()
        }

        // wrapping retrieveLoadEvents with function to filter out blocked resources
        val loadEvents = filterBlockedLoadEvents(resource, kafkaLoadService.retrieveLoadEvents(resourceType = resource, groupId = channelGroupId), tenantConfigService)
        if (loadEvents.isNotEmpty()) {
            return loadEvents.toLoadMirthMessages()
        }

        // wrapping retrievePublishedEvents with function to filter out blocked resources
        val adHocPublishEvents = filterBlockedPublishedEvents(resource, retrievePublishedEvents(DataTrigger.AD_HOC), tenantConfigService)
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
                    MirthKey.KAFKA_EVENT.code to it::class.simpleName!!,
                    MirthKey.EVENT_RUN_ID.code to it.metadata.runId
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
                    MirthKey.KAFKA_EVENT.code to it::class.simpleName!!,
                    MirthKey.EVENT_RUN_ID.code to it.metadata.runId
                )
            )
        }
    }
}
