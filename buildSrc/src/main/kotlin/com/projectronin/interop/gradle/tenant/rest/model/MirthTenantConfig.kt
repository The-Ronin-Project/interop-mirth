package com.projectronin.interop.gradle.tenant.rest.model

import java.time.OffsetDateTime
// MirthTenantConfig is copied from interop-proxy-server. If new attributes are added, we will need to include them.

data class MirthTenantConfig(
    val locationIds: List<String>,
    val lastUpdated: OffsetDateTime? = null,
)

private val SUPPORTED_KEYS = setOf("locationIds")

/**
 * Creates a new [MirthTenantConfig] based off the [valuesByName].
 */
fun createMirthTenantConfig(valuesByName: Map<String, String>): MirthTenantConfig {
    return mergeMirthTenantConfig(valuesByName, null)
}

/**
 * Merges the [tenantConfig] with the [valuesByName] to produce a new [MirthTenantConfig].
 */
fun mergeMirthTenantConfig(
    valuesByName: Map<String, String>,
    tenantConfig: MirthTenantConfig?,
): MirthTenantConfig {
    valuesByName.keys.forEach {
        if (!SUPPORTED_KEYS.contains(it)) {
            throw IllegalStateException("Unsupported configuration key found: $it")
        }
    }

    return MirthTenantConfig(
        locationIds = valuesByName["locationIds"]?.split(",") ?: tenantConfig?.locationIds ?: emptyList(),
    )
}
