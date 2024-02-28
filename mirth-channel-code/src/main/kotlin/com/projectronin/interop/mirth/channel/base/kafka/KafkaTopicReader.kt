package com.projectronin.interop.mirth.channel.base.kafka

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
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

    open fun List<InteropResourcePublishV1>.filterUnnecessaryEvents(): List<InteropResourcePublishV1> = this

    override val destinations: Map<String, KafkaEventResourcePublisher<*>> = mapOf("publish" to defaultPublisher)

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        // wrapping retrievePublishedEvents with function to filter out blocked resources
        val nightlyPublishedEvents =
            retrievePublishedEvents(DataTrigger.NIGHTLY)
                .filterAllowedPublishedResources()
                .filterUnnecessaryEvents()
        if (nightlyPublishedEvents.isNotEmpty()) {
            return nightlyPublishedEvents.toPublishMirthMessages()
        }

        // wrapping retrieveLoadEvents with function to filter out blocked resources
        val loadEvents =
            kafkaLoadService.retrieveLoadEvents(
                resourceType = resource,
                groupId = channelGroupId,
            ).filterAllowedLoadEventsResources()
        if (loadEvents.isNotEmpty()) {
            return loadEvents.toLoadMirthMessages()
        }

        // wrapping retrievePublishedEvents with function to filter out blocked resources
        // adHoc and backfill exist on the same topic, so when we call for ad-hoc we also get backfill events
        val adHocPublishEvents =
            retrievePublishedEvents(DataTrigger.AD_HOC)
                .filterAllowedPublishedResources()
                .filterUnnecessaryEvents()
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
                        MirthKey.EVENT_METADATA_SOURCE.code to key.third,
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

    private fun List<InteropResourcePublishV1>.filterAllowedPublishedResources(): List<InteropResourcePublishV1> {
        // group by tenant
        val groupedEvents = this.groupBy { it.tenantId }

        return groupedEvents.mapValues { (tenant, eventByTenant) ->
            // filter based on targeted or blocked
            // grab the resource list here because it's the same by tenant and we can avoid a db call
            val blockedResourceList = tenantConfigService.getConfiguration(tenant).blockedResources?.split(",")

            eventByTenant.filter {
                it.metadata.isAllowed(blockedResourceList)
            }
        }.values.flatten()
    }

    private fun List<InteropResourceLoadV1>.filterAllowedLoadEventsResources(): List<InteropResourceLoadV1> {
        // group by tenant
        val groupedEvents = this.groupBy { it.tenantId }
        return groupedEvents.mapValues { (tenant, eventByTenant) ->
            // filter based on targeted or blocked
            // grab the resource list here because it's the same by tenant and we can avoid a db call
            val blockedResourceList = tenantConfigService.getConfiguration(tenant).blockedResources?.split(",")

            eventByTenant.filter {
                it.metadata.isAllowed(blockedResourceList)
            }
        }.values.flatten()
    }

    private fun Metadata.isAllowed(blockedResourceList: List<String>?): Boolean {
        // we've actually found our resource in the targetedResourceList
        val isExplicitlyTargeted = this.targetedResources?.contains(resource.toString()) == true

        // either there is no list or it's empty
        val isImplicitlyTargeted = this.targetedResources.isNullOrEmpty()

        // we've deliberately blocked this resource type in our old tenant config
        val isBlocked =
            !blockedResourceList.isNullOrEmpty() &&
                resource.toString() in blockedResourceList

        return isExplicitlyTargeted ||
            (isImplicitlyTargeted && !isBlocked)
    }
}
