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
import org.springframework.stereotype.Component
import java.time.LocalDate

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

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        val tenants = tenantService.getAllTenants()
        val messages = tenants.map { tenant ->
            val locationIdsList = try {
                tenantConfigurationService.getLocationIDsByTenant(tenant.mnemonic)
            } catch (e: Exception) {
                logger.error(e) { "Failed to find configured locations for ${tenant.mnemonic}" }
                emptyList()
            }
            if (locationIdsList.isNotEmpty()) {
                locationIdsList.map {
                    MirthMessage(
                        message = it,
                        dataMap = mapOf(
                            MirthKey.TENANT_MNEMONIC.code to tenant.mnemonic,
                            "locationFhirID" to it // just so it's a little more apparent in later stages
                        ),
                    )
                }
            } else {
                // in the event that a tenant has an empty line in the DB we to generate a message,
                // so it errors, but we don't want this to error in the source reader or else all tenants will fail
                listOf(
                    MirthMessage(
                        message = "",
                        dataMap = mapOf(
                            MirthKey.TENANT_MNEMONIC.code to tenant.mnemonic,
                            "locationFhirID" to ""
                        ),
                    )
                )
            }
        }
        return messages.flatten()
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
        val patientsIds = fullAppointments.appointments.map {
            it.participant.single {
                it.actor?.reference?.value?.contains("Patient") == true
            }.actor!!.reference!!.value!!.removePrefix("Patient/")
        }.distinct()

        return MirthMessage(message = JacksonUtil.writeJsonValue(patientsIds))
    }
}
