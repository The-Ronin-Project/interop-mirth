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
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Collections
import kotlin.math.min

abstract class KafkaTopicReader(
    private val kafkaPublishService: KafkaPublishService,
    private val kafkaLoadService: KafkaLoadService,
    defaultPublisher: KafkaEventResourcePublisher<*>
) : TenantlessSourceService() {
    abstract val publishedResourcesSubscriptions: List<ResourceType>
    abstract val resource: ResourceType
    abstract val channelGroupId: String
    abstract val tenantConfigService: TenantConfigurationService

    open val maxBackfillDays: Int? = null
    open val maxEventBatchSize: Int = 20

    override val destinations: Map<String, KafkaEventResourcePublisher<*>> = mapOf("publish" to defaultPublisher)

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        // wrapping retrievePublishedEvents with function to filter out blocked resources
        val nightlyPublishedEvents =
            filterBlockedPublishedEvents(resource, retrievePublishedEvents(DataTrigger.NIGHTLY), tenantConfigService)
        if (nightlyPublishedEvents.isNotEmpty()) {
            return nightlyPublishedEvents.toPublishMirthMessages()
        }

        // wrapping retrieveLoadEvents with function to filter out blocked resources
        val loadEvents = filterBlockedLoadEvents(
            resource,
            kafkaLoadService.retrieveLoadEvents(
                resourceType = resource,
                groupId = channelGroupId
            ),
            tenantConfigService
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
            val splitBackfillEvents = if (backfillEvents.isNotEmpty()) {
                val splitBackfillEvents = backfillEvents.splitDateRange()
                splitBackfillEvents.toPublishMirthMessages()
            } else { emptyList() }

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
                    groupId = channelGroupId
                )
            }
    }

    private fun List<InteropResourcePublishV1>.toPublishMirthMessages(): List<MirthMessage> {
        return this.groupBy { Triple(it.tenantId, it.metadata.runId, it.resourceType) }.flatMap { (key, messages) ->
            messages.chunked(maxEventBatchSize).map { batch ->
                MirthMessage(
                    JacksonUtil.writeJsonValue(batch),
                    mapOf(
                        MirthKey.TENANT_MNEMONIC.code to key.first,
                        MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!,
                        MirthKey.EVENT_RUN_ID.code to key.second
                    )
                )
            }
        }
    }

    private fun List<InteropResourceLoadV1>.toLoadMirthMessages(): List<MirthMessage> {
        return this.groupBy { Pair(it.tenantId, it.metadata.runId) }.flatMap { (key, messages) ->
            messages.chunked(maxEventBatchSize).map { batch ->
                MirthMessage(
                    JacksonUtil.writeJsonValue(batch),
                    mapOf(
                        MirthKey.TENANT_MNEMONIC.code to key.first,
                        MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!,
                        MirthKey.EVENT_RUN_ID.code to key.second
                    )
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
        val oldBackfillInfo = oldMetadata.backfillRequst!!
        val startDate = oldBackfillInfo.backfillStartDate!!
        val endDate = oldBackfillInfo.backfillEndDate!!
        val dayPairs = splitDateRange(startDate, endDate, maxBackfillDays!!)
        return dayPairs.map {
            val backfillModified = oldBackfillInfo.copy(
                backfillStartDate = it.first,
                backfillEndDate = it.second
            )
            val modifiedMetad = oldMetadata.copy(backfillRequst = backfillModified)
            this.copy(metadata = modifiedMetad)
        }
    }

    // split a given set of dates into a list of pairs of roughly equal length
    private fun splitDateRange(startDate: OffsetDateTime, endDate: OffsetDateTime, maxDayRange: Int): List<Pair<OffsetDateTime, OffsetDateTime>> {
        // add one, since this function technically returns the number of days BETWEEN two days
        // don't need to add 2 tho, because the range function we use later has an inclusive end
        val daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1
        // don't step by more days than there are in the entire range
        val stepSize = min(daysBetween, maxDayRange.toLong())
        val dateRange = 0 until daysBetween step stepSize
        return dateRange.map {
            val sectionStart = startDate.plusDays(it)
            // don't step past the end date
            val sectionEnd = Collections.min(listOf(endDate, sectionStart.plusDays(stepSize)))
            Pair(sectionStart, sectionEnd)
        }
    }
}
