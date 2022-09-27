package com.projectronin.interop.mirth.connector.util

import com.projectronin.interop.mirth.connector.util.DatabaseUtil.tenantDatabase
import com.projectronin.interop.tenant.config.EHRTenantDAOFactory
import com.projectronin.interop.tenant.config.ProviderPoolService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.EpicTenantDAO
import com.projectronin.interop.tenant.config.data.ProviderPoolDAO
import com.projectronin.interop.tenant.config.data.TenantDAO

/**
 * Utility providing access to Tenant services.
 */
internal object TenantUtil {
    private val tenantDAO = TenantDAO(tenantDatabase)
    private val ehrDAO = EhrDAO(tenantDatabase)
    private val epicTenantDAO = EpicTenantDAO(tenantDatabase)
    val tenantService = TenantService(tenantDAO, ehrDAO, EHRTenantDAOFactory(epicTenantDAO))

    private val providerPoolDAO = ProviderPoolDAO(tenantDatabase)
    val providerPoolService = ProviderPoolService(providerPoolDAO)
}
