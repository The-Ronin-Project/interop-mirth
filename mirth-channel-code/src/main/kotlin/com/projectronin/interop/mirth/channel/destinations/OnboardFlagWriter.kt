package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.mirth.channel.base.DestinationConfiguration
import com.projectronin.interop.mirth.channel.base.JavaScriptDestinationConfiguration
import com.projectronin.interop.mirth.channel.base.MirthFilter
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.stereotype.Component

@Component
class OnboardFlagWriter(
    private val ehrFactory: EHRFactory,
    private val tenantService: TenantService,
    private val tenantConfigurationService: TenantConfigurationService
) :
    TenantlessDestinationService() {

    override fun getConfiguration(): DestinationConfiguration =
        JavaScriptDestinationConfiguration(name = "Write Onboard Flag")

    override fun getFilter(): MirthFilter? {
        return object : MirthFilter {
            override fun filter(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthFilterResponse {
                val blockedResourceList =
                    tenantConfigurationService.getConfiguration(tenantMnemonic).blockedResources?.split(",")
                        ?: return MirthFilterResponse(true)
                return MirthFilterResponse("PatientOnboardFlag" !in blockedResourceList)
            }
        }
    }

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val onboardService = vendorFactory.onboardFlagService
        return try {
            onboardService.setOnboardedFlag(tenant, sourceMap[MirthKey.FHIR_ID.code] as String)
            MirthResponse(status = MirthResponseStatus.SENT)
        } catch (e: Exception) {
            MirthResponse(status = MirthResponseStatus.ERROR, message = e.message)
        }
    }
}
