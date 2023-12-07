package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.tenant.rest.TenantRestClient
import com.projectronin.interop.gradle.tenant.rest.model.createTenantServer
import com.projectronin.interop.gradle.tenant.rest.model.mergeTenantServer
import io.ktor.http.isSuccess

open class UpdateTenantServerTask : BaseTenantServerTask() {
    override val subfolder = "tenant-server"

    override fun updateConfig(
        tenantMnemonic: String,
        client: TenantRestClient,
        config: Map<String, String>,
    ) {
        val currentTenantServers = client.getTenantServers(tenantMnemonic)
        val messageTypes = listOf("MDM")
        messageTypes.forEach { messageType ->
            val currentTenantServer = currentTenantServers?.firstOrNull { it.messageType == messageType }
            val status =
                if (currentTenantServer == null) {
                    logger.lifecycle("Posting new tenant server for $tenantMnemonic")
                    val newConfig = createTenantServer(config, messageType)
                    client.postTenantServer(tenantMnemonic, newConfig)
                } else {
                    val updatedConfig = mergeTenantServer(config, messageType, currentTenantServer)
                    logger.lifecycle("Updating tenant server for $tenantMnemonic")
                    client.putTenantServer(tenantMnemonic, updatedConfig)
                }
            if (status.isSuccess()) {
                logger.lifecycle("Updated tenant Server for $tenantMnemonic")
            } else {
                throw RuntimeException("Unsuccessful status code $status returned while updating tenant server for $tenantMnemonic")
            }
        }
    }
}
