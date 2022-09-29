package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.tenant.rest.TenantRestClient
import com.projectronin.interop.gradle.tenant.rest.model.createMirthTenantConfig
import com.projectronin.interop.gradle.tenant.rest.model.mergeMirthTenantConfig
import io.ktor.http.isSuccess

/**
 * Task for reading and updating the Tenant Config.
 */
open class UpdateTenantConfigTask : BaseTenantServerTask() {
    override val subfolder = "mirth-config"

    override fun updateConfig(tenantMnemonic: String, client: TenantRestClient, config: Map<String, String>) {
        val currentConfig = client.getMirthTenantConfig(tenantMnemonic)
        logger.lifecycle(currentConfig.toString())
        val status = if (currentConfig == null) {
            logger.lifecycle("Posting new config for $tenantMnemonic")
            val newConfig = createMirthTenantConfig(config)
            client.postMirthTenantConfig(tenantMnemonic, newConfig)
        } else {
            val updatedConfig = mergeMirthTenantConfig(config, currentConfig)
            logger.lifecycle("Updating new config for $tenantMnemonic")
            client.putMirthTenantConfig(tenantMnemonic, updatedConfig)
        }

        if (status.isSuccess()) {
            logger.lifecycle("Updated config for $tenantMnemonic")
        } else {
            throw RuntimeException("Unsuccessful status code $status returned while updating config for $tenantMnemonic")
        }
    }
}
