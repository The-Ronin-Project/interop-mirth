package com.projectronin.interop.mirth.channel

import com.projectronin.clinical.trial.client.ClinicalTrialClient
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.backfill.client.QueueClient
import com.projectronin.interop.backfill.client.generated.models.BackfillStatus
import com.projectronin.interop.backfill.client.generated.models.QueueEntry
import com.projectronin.interop.backfill.client.generated.models.UpdateQueueEntry
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.PatientDiscoveryWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.channel.util.getMetadata
import com.projectronin.interop.mirth.channel.util.serialize
import com.projectronin.interop.mirth.channel.util.unlocalize
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset

@Component
class PatientDiscovery(
    val tenantService: TenantService,
    patientDiscoveryWriter: PatientDiscoveryWriter,
    private val ehrFactory: EHRFactory,
    private val tenantConfigurationService: TenantConfigurationService,
    @Value("\${backfill.enabled:no}")
    private val backfillEnabledString: String,
    private val backfillQueueClient: QueueClient,
    private val clinicalTrialClient: ClinicalTrialClient,
) : TenantlessSourceService() {
    override val rootName = "PatientDiscovery"
    override val destinations = mapOf("Kafka" to patientDiscoveryWriter)
    private val futureDateRange: Long = 7
    private val pastDateRange: Long = 1
    private val backfillEnabled = backfillEnabledString.lowercase() == "yes"
    private val clinicalTrialLocation = "ClinicalTrialLoadLocation"

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(PatientDiscovery::class.java)
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        val nightlyMessages =
            tenantService.getMonitoredTenants()
                .filter { needsLoad(it) }
                .mapNotNull { tenant ->
                    try {
                        val locations = tenantConfigurationService.getLocationIDsByTenant(tenant.mnemonic)
                        if (locations.isEmpty()) {
                            null
                        } else {
                            val metadata = generateMetadata()
                            MirthMessage(
                                message = JacksonUtil.writeJsonValue(locations),
                                dataMap =
                                    mapOf(
                                        MirthKey.TENANT_MNEMONIC.code to tenant.mnemonic,
                                        MirthKey.EVENT_METADATA.code to serialize(metadata),
                                        MirthKey.EVENT_RUN_ID.code to metadata.runId,
                                    ),
                            )
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to find configured locations for ${tenant.mnemonic}" }
                        null
                    }
                }
        if (nightlyMessages.isNotEmpty()) {
            return nightlyMessages
        } else if (backfillEnabled) {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val tenantsOkToRun =
                tenantService
                    .getAllTenants()
                    // we should be in a tenant's window to poll
                    .filter { AvailableWindow(it).isInWindow(now) }
            // loop through the tenants and take the first tenant we find with a backfill entry
            val backfillQueueEntry =
                tenantsOkToRun
                    .asSequence()
                    .map { runBlocking { backfillQueueClient.getQueueEntries(it.mnemonic).firstOrNull() } }
                    .firstNotNullOfOrNull { it }

            backfillQueueEntry?.let { queueEntry ->
                val tenantTimezone =
                    tenantsOkToRun
                        .single { it.mnemonic == queueEntry.tenantId }
                        .timezone.rules.getOffset(LocalDateTime.now())
                val metadata =
                    generateMetadata(
                        backfillInfo =
                            Metadata.BackfillRequest(
                                backfillId = queueEntry.backfillId.toString(),
                                // these are offset date times, but backfill provides localdate time
                                // offsetdatetime is more precise than we probably care about
                                backfillStartDate =
                                    OffsetDateTime.of(
                                        queueEntry.startDate.atStartOfDay(),
                                        tenantTimezone,
                                    ),
                                backfillEndDate =
                                    OffsetDateTime.of(
                                        queueEntry.endDate.atStartOfDay(),
                                        tenantTimezone,
                                    ),
                            ),
                    )
                return listOf(
                    MirthMessage(
                        message = JacksonUtil.writeJsonValue(queueEntry),
                        dataMap =
                            mapOf(
                                MirthKey.TENANT_MNEMONIC.code to queueEntry.tenantId,
                                MirthKey.EVENT_METADATA.code to serialize(metadata),
                                MirthKey.EVENT_RUN_ID.code to metadata.runId,
                            ),
                    ),
                )
            }
        }

        // either didn't find any message or backfill wasn't enabled or backfill didn't find messages
        return emptyList()
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthMessage {
        val metadata = getMetadata(sourceMap)

        // if it's a backfill request we already have the patient id, just grab that and stop processing
        if (metadata.backfillRequest != null) {
            val backfillEntry = JacksonUtil.readJsonObject(msg, QueueEntry::class)
            // tell backfill we're (re)processing this
            runBlocking {
                backfillQueueClient.updateQueueEntryByID(backfillEntry.id, UpdateQueueEntry(BackfillStatus.STARTED))
            }
            return MirthMessage(message = JacksonUtil.writeJsonValue(listOf(backfillEntry.patientId)))
        }

        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic) ?: throw Exception("No Tenant Found")

        val locations = JacksonUtil.readJsonList(msg, String::class).toMutableList()
        val patientIds = mutableListOf<String>()

        // Clinical trial patient discovery
        if (locations.contains(clinicalTrialLocation)) {
            val clinicalTrialSubjects = runBlocking { clinicalTrialClient.getSubjects(true) }
            val clinicalTrialPatients =
                clinicalTrialSubjects.filter { it.roninFhirId.startsWith("$tenantMnemonic-") }
                    .map { "Patient/${it.roninFhirId.unlocalize(tenant)}" }
            patientIds.addAll(clinicalTrialPatients)

            locations.remove(clinicalTrialLocation)
        }

        // Normal patient discovery
        if (locations.isNotEmpty()) {
            val locationBasedPatients = getPatientIdsFromLocations(locations, tenant)
            patientIds.addAll(locationBasedPatients)
        }

        return MirthMessage(message = JacksonUtil.writeJsonValue(patientIds))
    }

    private fun getPatientIdsFromLocations(
        locations: List<String>,
        tenant: Tenant,
    ): List<String> {
        val currentDate = LocalDate.now()
        val endDate = currentDate.plusDays(futureDateRange)
        val startDate = currentDate.minusDays(pastDateRange)

        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val fullAppointments =
            vendorFactory.appointmentService.findLocationAppointments(
                tenant,
                locations,
                startDate,
                endDate,
            )

        return fullAppointments.appointments.flatMap { appointment ->
            appointment.participant.mapNotNull { it.actor?.reference?.value }
                .filter { it.contains("Patient") }
        }.distinct()
    }

    // Given a tenantDO, should we run for tonight's nightly load?
    fun needsLoad(tenant: Tenant): Boolean {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val config =
            try {
                tenantConfigurationService.getConfiguration(tenant.mnemonic)
            } catch (e: IllegalArgumentException) {
                logger.warn { "No Mirth Tenant Config found for tenant: ${tenant.mnemonic}" }
                return false // don't attempt to load a tenant with no config
            }

        val shouldRun = AvailableWindow(tenant).shouldRun(now, config.lastUpdated)
        return if (shouldRun) {
            config.lastUpdated = now
            tenantConfigurationService.updateConfiguration(config)
            true
        } else {
            false
        }
    }

    /***
     * Helper class which takes a tenant, and creates an object which represents the "Availability Window" for when are
     * allowed to call this tenant
     * Tenants with no config default to always being available
     */
    class AvailableWindow(tenant: Tenant) {
        private val tenantTimeZone = tenant.timezone.rules.getOffset(LocalDateTime.now())

        // These are public mostly to make testing easier
        val windowStartTime: OffsetTime =
            tenant.batchConfig?.availableStart?.atOffset(tenantTimeZone)
                ?: OffsetTime.MIN.withOffsetSameLocal(tenantTimeZone)
        val windowEndTime: OffsetTime =
            tenant.batchConfig?.availableEnd?.atOffset(tenantTimeZone)
                ?: OffsetTime.MAX.withOffsetSameLocal(tenantTimeZone)
        val spansMidnight: Boolean = windowStartTime > windowEndTime

        // Is right now in the current window?
        fun isInWindow(now: OffsetDateTime): Boolean {
            val nowInTenantTimeZone = now.withOffsetSameInstant(tenantTimeZone)
            val currentWindow = getCurrentWindow(nowInTenantTimeZone)
            return nowInTenantTimeZone in currentWindow.first..currentWindow.second
        }

        // given a OffsetDateTime, calculate the current window
        private fun getCurrentWindow(now: OffsetDateTime): Pair<OffsetDateTime, OffsetDateTime> {
            val midnightLocalTime = OffsetTime.MIN.withOffsetSameLocal(tenantTimeZone)
            val betweenMidnightAndEndTime = now.toOffsetTime() in midnightLocalTime..windowEndTime
            if (spansMidnight) {
                // if we're in the early morning and the window is still open from last night
                return if (betweenMidnightAndEndTime) {
                    val currentWindowOpen = now.with(windowStartTime).minusDays(1)
                    val currentWindowEnd = now.with(windowEndTime)
                    Pair(currentWindowOpen, currentWindowEnd)
                } else {
                    val currentWindowOpen = now.with(windowStartTime)
                    val currentWindowEnd = now.with(windowEndTime).plusDays(1)
                    Pair(currentWindowOpen, currentWindowEnd)
                }

                // same day window, it's just today
            } else {
                val currentWindowOpen = now.with(windowStartTime)
                val currentWindowEnd = now.with(windowEndTime)
                return Pair(currentWindowOpen, currentWindowEnd)
            }
        }

        fun ranTodayAlready(
            now: OffsetDateTime,
            lastRunTime: OffsetDateTime?,
        ): Boolean {
            val nowInTenantTimeZone = now.withOffsetSameInstant(tenantTimeZone)
            val lastRunTimeInTenantTimeZone = lastRunTime?.withOffsetSameInstant(tenantTimeZone)
            return if (lastRunTimeInTenantTimeZone == null) {
                false
                // somehow the last time we ran is in the future, so we've already run
            } else if (nowInTenantTimeZone <= lastRunTimeInTenantTimeZone) {
                return true
            } else {
                val currentWindow = getCurrentWindow(nowInTenantTimeZone)
                return lastRunTimeInTenantTimeZone in currentWindow.first..currentWindow.second
            }
        }

        fun shouldRun(
            now: OffsetDateTime,
            lastRunTime: OffsetDateTime?,
        ): Boolean {
            return isInWindow(now) && !ranTodayAlready(now, lastRunTime)
        }
    }
}
