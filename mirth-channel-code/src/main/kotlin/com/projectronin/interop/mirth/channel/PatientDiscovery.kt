package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.PatientDiscoveryWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
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
) : TenantlessSourceService() {
    override val rootName = "PatientDiscovery"
    override val destinations = mapOf("Kafka" to patientDiscoveryWriter)
    private val futureDateRange: Long = 7
    private val pastDateRange: Long = 1
    lateinit var map: MutableMap<ResourceType, MutableMap<String, Int>>

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(PatientDiscovery::class.java)
    }

    fun needsLoad(tenant: Tenant): Boolean {
        if (!isTimeInRange(tenant)) return false
        val config = tenantConfigurationService.getConfiguration(tenant.mnemonic)
        // either this tenant has never had a load, or it has been at least 24 hours.
        return if (config.lastUpdated == null ||
            config.lastUpdated!! <= OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
        ) {
            config.lastUpdated = OffsetDateTime.now(ZoneOffset.UTC)
            tenantConfigurationService.updateConfiguration(config)
            true
        } else false
    }

    fun isTimeInRange(tenant: Tenant): Boolean {
        val batch = tenant.batchConfig ?: return true // if no batch config, we are good to go
        val tz = tenant.timezone.rules.getOffset(LocalDateTime.now())
        val now = OffsetTime.now(ZoneOffset.UTC)
        val start = batch.availableStart.atOffset(tz)
        val end = batch.availableEnd.atOffset(tz)
        return if (start < end) {
            now in start..end
        } else {
            now >= start || now <= end
        }
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        return tenantService.getAllTenants()
            .filter { needsLoad(it) }
            .flatMap { tenant ->
                try {
                    tenantConfigurationService.getLocationIDsByTenant(tenant.mnemonic)
                        .map { locationId ->
                            MirthMessage(
                                message = locationId,
                                dataMap = mapOf(
                                    MirthKey.TENANT_MNEMONIC.code to tenant.mnemonic,
                                    "locationFhirID" to locationId
                                )
                            )
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to find configured locations for ${tenant.mnemonic}" }
                    emptyList()
                }
            }
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val currentDate = LocalDate.now()
        val endDate = currentDate.plusDays(futureDateRange)
        val startDate = currentDate.minusDays(pastDateRange)
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic) ?: throw Exception("No Tenant Found")

        val vendorFactory = ehrFactory.getVendorFactory(tenant)

        val fullAppointments = vendorFactory.appointmentService.findLocationAppointments(
            tenant,
            listOf(msg),
            startDate,
            endDate
        )

        val usefulParticipants = fullAppointments.appointments.flatMap { appointment ->
            appointment.participant.mapNotNull { it.actor?.reference?.value }
                .filter { it.contains("Patient") || it.contains("Practitioner") }
        }.distinct()

        return MirthMessage(message = JacksonUtil.writeJsonValue(usefulParticipants))
    }
}
