package com.projectronin.interop.tenant.config.exception

/**
 * A channel is unusable without knowing the tenant mnemonic.
 */
class TenantMissingException() : Exception("Could not get tenant information for the channel")
