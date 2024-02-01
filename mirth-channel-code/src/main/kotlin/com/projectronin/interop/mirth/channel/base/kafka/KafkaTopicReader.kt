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
import com.projectronin.interop.mirth.channel.util.splitDateRange
import com.projectronin.interop.mirth.service.TenantConfigurationService

abstract class KafkaTopicReader(
    private val kafkaPublishService: KafkaPublishService,
    private val kafkaLoadService: KafkaLoadService,
    defaultPublisher: KafkaEventResourcePublisher<*>,
) : TenantlessSourceService() {
    abstract val publishedResourcesSubscriptions: List<ResourceType>
    abstract val resource: ResourceType
    abstract val channelGroupId: String
    abstract val tenantConfigService: TenantConfigurationService

    open val maxBackfillDays: Int? = null
    open val maxEventBatchSize: Int = 20

    open val publishEventOverrideBatchSize: Int? = 1
    open val publishEventOverrideResources: List<ResourceType> = emptyList()

    override val destinations: Map<String, KafkaEventResourcePublisher<*>> = mapOf("publish" to defaultPublisher)

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        // wrapping retrievePublishedEvents with function to filter out blocked resources
        val nightlyPublishedEvents =
            filterBlockedPublishedEvents(resource, retrievePublishedEvents(DataTrigger.NIGHTLY), tenantConfigService)
        if (nightlyPublishedEvents.isNotEmpty()) {
            return nightlyPublishedEvents.toPublishMirthMessages()
        }

        // wrapping retrieveLoadEvents with function to filter out blocked resources
        val loadEvents =
            filterBlockedLoadEvents(
                resource,
                kafkaLoadService.retrieveLoadEvents(
                    resourceType = resource,
                    groupId = channelGroupId,
                ),
                tenantConfigService,
            )
        if (loadEvents.isNotEmpty()) {
            return loadEvents.toLoadMirthMessages()
        }

        // wrapping retrievePublishedEvents with function to filter out blocked resources
        // adHoc and backfill exist on the same topic, so when we call for ad-hoc we also get backfill events
        val adHocPublishEvents =
            filterBlockedPublishedEvents(resource, retrievePublishedEvents(DataTrigger.AD_HOC), tenantConfigService)
        if (adHocPublishEvents.isNotEmpty()) {
            // split the events by trigger, so we can make sure the backfill events are split as needed
            val groupedEvents = adHocPublishEvents.groupBy { it.dataTrigger!! }
            val backfillEvents = groupedEvents[InteropResourcePublishV1.DataTrigger.backfill] ?: emptyList()
            val adHocEvents = groupedEvents[InteropResourcePublishV1.DataTrigger.adhoc] ?: emptyList()

            // if we've got any backfill events we can split them here based on date range
            val splitBackfillEvents =
                if (backfillEvents.isNotEmpty()) {
                    val splitBackfillEvents = backfillEvents.splitDateRange()
                    splitBackfillEvents.toPublishMirthMessages()
                } else {
                    emptyList()
                }

            return adHocEvents.toPublishMirthMessages() + splitBackfillEvents
        }

        return emptyList()
    }

    // Grab the first set of published events from the subscribed resources
    private fun retrievePublishedEvents(dataTrigger: DataTrigger): List<InteropResourcePublishV1> {
        return publishedResourcesSubscriptions
            .flatMap {
                kafkaPublishService.retrievePublishEvents(
                    resourceType = it,
                    dataTrigger = dataTrigger,
                    groupId = channelGroupId,
                )
            }
    }

    private fun List<InteropResourcePublishV1>.toPublishMirthMessages(): List<MirthMessage> {
        return this.groupBy { Triple(it.tenantId, it.metadata.runId, it.resourceType) }.flatMap { (key, messages) ->
            val batchSize = getBatchSize(true, messages.first().resourceType)
            messages.chunked(batchSize).map { batch ->
                MirthMessage(
                    JacksonUtil.writeJsonValue(batch),
                    mapOf(
                        MirthKey.TENANT_MNEMONIC.code to key.first,
                        MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!,
                        MirthKey.EVENT_RUN_ID.code to key.second,
                    ),
                )
            }
        }
    }

    private fun List<InteropResourceLoadV1>.toLoadMirthMessages(): List<MirthMessage> {
        return this.groupBy { Pair(it.tenantId, it.metadata.runId) }.flatMap { (key, messages) ->
            val batchSize = getBatchSize(false)
            messages.chunked(batchSize).map { batch ->
                MirthMessage(
                    JacksonUtil.writeJsonValue(batch),
                    mapOf(
                        MirthKey.TENANT_MNEMONIC.code to key.first,
                        MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!,
                        MirthKey.EVENT_RUN_ID.code to key.second,
                    ),
                )
            }
        }
    }

    private fun List<InteropResourcePublishV1>.splitDateRange(): List<InteropResourcePublishV1> {
        if (maxBackfillDays == null) return this
        return this.map { it.splitByDateRange() }.flatten()
    }

    // Given an event, decide if the date range is too big and split
    private fun InteropResourcePublishV1.splitByDateRange(): List<InteropResourcePublishV1> {
        val oldMetadata = this.metadata
        val oldBackfillInfo = oldMetadata.backfillRequest ?: return listOf(this)
        val startDate = oldBackfillInfo.backfillStartDate
        val endDate = oldBackfillInfo.backfillEndDate
        val dayPairs = splitDateRange(startDate, endDate, maxBackfillDays!!)
        return dayPairs.map {
            val backfillModified =
                oldBackfillInfo.copy(
                    backfillStartDate = it.first,
                    backfillEndDate = it.second,
                )
            val modifiedMetadata = oldMetadata.copy(backfillRequest = backfillModified)
            this.copy(metadata = modifiedMetadata)
        }
    }

    // Determine batch size based on kafka event type and fhir resource
    private fun getBatchSize(
        isPublish: Boolean,
        eventType: ResourceType? = null,
    ): Int {
        if (isPublish && publishEventOverrideResources.contains(eventType)) {
            return publishEventOverrideBatchSize ?: maxEventBatchSize
        }
        return maxEventBatchSize
    }
}
