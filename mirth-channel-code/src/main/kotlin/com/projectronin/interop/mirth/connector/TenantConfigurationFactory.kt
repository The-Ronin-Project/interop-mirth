package com.projectronin.interop.mirth.connector

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.mirth.connector.util.SpringUtil
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.data.TenantServerDAO
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO

interface TenantConfigurationFactory {
    fun getLocationIDsByTenant(tenantMnemonic: String): List<String>
    fun getMDMInfo(tenantMnemonic: String): Pair<String, Int>?
}

internal object TenantConfigurationFactoryImpl : TenantConfigurationFactory {
    val context by lazy { SpringUtil.applicationContext }
    override fun getLocationIDsByTenant(tenantMnemonic: String): List<String> {
        return getConfiguration(tenantMnemonic).locationIds.splitToSequence(",").toList()
    }

    private fun getConfiguration(tenantMnemonic: String): MirthTenantConfigDO {
        return context.getBean(MirthTenantConfigDAO::class.java).getByTenantMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("No Mirth Tenant Configuration object found for $tenantMnemonic")
    }

    override fun getMDMInfo(tenantMnemonic: String): Pair<String, Int>? {
        val info =
            context.getBean(TenantServerDAO::class.java).getTenantServers(tenantMnemonic, MessageType.MDM).firstOrNull()
        return info?.let {
            Pair(info.address, info.port)
        }
    }
}
