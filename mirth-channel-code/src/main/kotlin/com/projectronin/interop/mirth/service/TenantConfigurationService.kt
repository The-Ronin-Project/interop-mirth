package com.projectronin.interop.mirth.service

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.data.TenantServerDAO
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import org.springframework.stereotype.Component

@Component
class TenantConfigurationService(
    private val mirthTenantConfigDAO: MirthTenantConfigDAO,
    private val tenantServerDAO: TenantServerDAO
) {
    fun getLocationIDsByTenant(tenantMnemonic: String): List<String> {
        return getConfiguration(tenantMnemonic).locationIds.splitToSequence(",").toList()
    }

    private fun getConfiguration(tenantMnemonic: String): MirthTenantConfigDO {
        return mirthTenantConfigDAO.getByTenantMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("No Mirth Tenant Configuration object found for $tenantMnemonic")
    }

    fun getMDMInfo(tenantMnemonic: String): Pair<String, Int>? {
        val info = tenantServerDAO.getTenantServers(tenantMnemonic, MessageType.MDM).firstOrNull()
        return info?.let {
            Pair(info.address, info.port)
        }
    }
}
