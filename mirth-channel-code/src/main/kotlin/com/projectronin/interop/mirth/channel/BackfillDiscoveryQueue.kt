package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.backfill.client.DiscoveryQueueClient
import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueEntry
import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueStatus
import com.projectronin.interop.backfill.client.generated.models.UpdateDiscoveryEntry
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.BackfillDiscoveryQueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.util.serialize
import com.projectronin.interop.mirth.models.MirthMessage
import com.projectronin.interop.mirth.models.polling.IntervalPollingConfig
import com.projectronin.interop.mirth.models.polling.PollingConfig
import com.projectronin.interop.mirth.models.transformer.MirthTransformer
import com.projectronin.interop.tenant.config.TenantService
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes

@Component
class BackfillDiscoveryQueue(
    val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val discoveryQueueClient: DiscoveryQueueClient,
    writer: BackfillDiscoveryQueueWriter
) : TenantlessSourceService() {
    override val rootName = "BackfillDiscoveryQueue"
    override val destinations = mapOf("queue" to writer)

    companion object : ChannelConfiguration<BackfillDiscoveryQueue>() {
        override val channelClass = BackfillDiscoveryQueue::class
        override val id = "cf0088fe-c085-491f-a926-c498b68e2ef7"
        override val description =
            "Polls the backfill service for new discovery queue entries"
        override val metadataColumns: Map<String, String> = mapOf(
            "TENANT" to "tenantMnemonic",
            "FHIRID" to "locationFhirID",
            "BACKFILLID" to "backfillID"
        )

        override val pollingConfig: PollingConfig = IntervalPollingConfig(
            pollingFrequency = 30.minutes
        )
        override val sourceThreads: Int = 2
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        return tenantService.getMonitoredTenants()
            .mapNotNull { tenant ->
                try {
                    val queueEntries =
                        runBlocking {
                            discoveryQueueClient.getDiscoveryQueueEntries(
                                tenant.mnemonic,
                                DiscoveryQueueStatus.UNDISCOVERED
                            )
                        }
                    if (queueEntries.isEmpty()) {
                        null
                    } else {
                        queueEntries.map {
                            val metadata = Metadata(
                                runId = it.backfillId.toString(),
                                runDateTime = OffsetDateTime.now(ZoneOffset.UTC)
                            )
                            MirthMessage(
                                message = JacksonUtil.writeJsonValue(it),
                                dataMap = mapOf(
                                    MirthKey.TENANT_MNEMONIC.code to tenant.mnemonic,
                                    MirthKey.EVENT_METADATA.code to serialize(metadata),
                                    MirthKey.EVENT_RUN_ID.code to metadata.runId,
                                    MirthKey.BACKFILL_ID.code to it.backfillId.toString()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to find configured locations for ${tenant.mnemonic}" }
                    null
                }
            }.flatten()
    }

    override fun getSourceTransformer(): MirthTransformer? {
        return object : MirthTransformer {
            override fun transform(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthMessage {
                val queueEntry = JacksonUtil.readJsonObject(msg, DiscoveryQueueEntry::class)
                runBlocking {
                    discoveryQueueClient.updateDiscoveryQueueEntryByID(
                        queueEntry.id,
                        UpdateDiscoveryEntry(DiscoveryQueueStatus.DISCOVERED)
                    )
                }
                val tenant = tenantService.getTenantForMnemonic(tenantMnemonic) ?: throw Exception("No Tenant Found")

                val vendorFactory = ehrFactory.getVendorFactory(tenant)
                val location = queueEntry.locationId
                val fullAppointments = vendorFactory.appointmentService.findLocationAppointments(
                    tenant,
                    listOf(location),
                    queueEntry.startDate,
                    queueEntry.endDate
                )

                val patients = fullAppointments.appointments.flatMap { appointment ->
                    appointment.participant.mapNotNull { it.actor?.reference?.value }
                        .filter { it.contains("Patient") }
                }.distinct()

                return MirthMessage(message = JacksonUtil.writeJsonValue(patients))
            }
        }
    }
}
