package com.projectronin.interop.gradle.tenant.rest.model

data class TenantServer(
    val id: Int?,
    val messageType: String = "MDM",
    val address: String,
    val port: Int,
    val serverType: String = "N",
)

private val SUPPORTED_KEYS = setOf("address", "port")

fun createTenantServer(
    valuesByName: Map<String, String>,
    messageType: String,
): TenantServer {
    return mergeTenantServer(valuesByName, messageType, null)
}

/**
 * Merges the [tenantConfig] with the [valuesByName] to produce a new [MirthTenantConfig].
 */
fun mergeTenantServer(
    valuesByName: Map<String, String>,
    messageType: String,
    tenantServer: TenantServer?,
): TenantServer {
    valuesByName.keys.forEach {
        if (!SUPPORTED_KEYS.contains(it.substringAfter("$messageType."))) {
            throw IllegalStateException("Unsupported configuration key found: $it")
        }
    }

    return TenantServer(
        id = tenantServer?.id,
        messageType = tenantServer?.messageType ?: messageType,
        address = valuesByName["$messageType.address"] ?: tenantServer?.address ?: "",
        port = valuesByName["$messageType.port"]?.toInt() ?: tenantServer?.port ?: 1,
    )
}
