package com.projectronin.interop.mirth.channel

import com.projectronin.interop.backfill.client.DiscoveryQueueClient
import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueEntry
import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueStatus
import com.projectronin.interop.backfill.client.generated.models.UpdateDiscoveryEntry
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.BackfillDiscoveryQueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.util.splitDateRange
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.tenant.config.TenantService
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Component
class BackfillDiscoveryQueue(
    val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val discoveryQueueClient: DiscoveryQueueClient,
    writer: BackfillDiscoveryQueueWriter,
    @Value("\${backfill.discovery.window:30}")
    private val maximumDayRange: Int = 30,
) : TenantlessSourceService() {
    override val rootName = "BackfillDiscoveryQueue"
    override val destinations = mapOf("queue" to writer)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(BackfillDiscoveryQueue::class.java)
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        return tenantService.getMonitoredTenants()
            .mapNotNull { tenant ->
                try {
                    val queueEntries =
                        runBlocking {
                            discoveryQueueClient.getDiscoveryQueueEntries(
                                tenant.mnemonic,
                                DiscoveryQueueStatus.UNDISCOVERED,
                            )
                        }
                    if (queueEntries.isEmpty()) {
                        null
                    } else {
                        queueEntries
                            // first split the date range on these, so appointment searches will be smaller chunks
                            .map { queue ->
                                val tenantTimezone = tenant.timezone.rules.getOffset(LocalDateTime.now())
                                val offsetStartDate =
                                    OffsetDateTime.of(
                                        queue.startDate.atStartOfDay(),
                                        tenantTimezone,
                                    )
                                val offsetEndRangeDate =
                                    OffsetDateTime.of(
                                        queue.endDate.atStartOfDay(),
                                        tenantTimezone,
                                    )
                                val range = splitDateRange(offsetStartDate, offsetEndRangeDate, maximumDayRange)
                                range.map {
                                    queue.copy(
                                        startDate = it.first.toLocalDate(),
                                        endDate = it.second.toLocalDate(),
                                    )
                                }
                            }
                            .flatten()
                            // now convert these entries to mirth messages
                            .map {
                                MirthMessage(
                                    message = JacksonUtil.writeJsonValue(it),
                                    dataMap =
                                        mapOf(
                                            MirthKey.TENANT_MNEMONIC.code to tenant.mnemonic,
                                            MirthKey.BACKFILL_ID.code to it.backfillId.toString(),
                                        ),
                                )
                            }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to poll for backfill discovery queue for ${tenant.mnemonic}" }
                    null
                }
            }.flatten()
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthMessage {
        val queueEntry = JacksonUtil.readJsonObject(msg, DiscoveryQueueEntry::class)
        runBlocking {
            discoveryQueueClient.updateDiscoveryQueueEntryByID(
                queueEntry.id,
                UpdateDiscoveryEntry(DiscoveryQueueStatus.DISCOVERED),
            )
        }
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic) ?: throw Exception("No Tenant Found")

        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val location = queueEntry.locationId
        val fullAppointments =
            vendorFactory.appointmentService.findLocationAppointments(
                tenant,
                listOf(location),
                queueEntry.startDate,
                queueEntry.endDate,
            )

        val patients =
            fullAppointments.appointments.flatMap { appointment ->
                appointment.participant.mapNotNull { it.actor?.reference?.value }
                    .filter { it.contains("Patient") }
            }.distinct()

        return MirthMessage(message = JacksonUtil.writeJsonValue(patients))
    }
}
