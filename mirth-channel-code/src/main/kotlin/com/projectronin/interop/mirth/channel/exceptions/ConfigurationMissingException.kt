package com.projectronin.interop.tenant.config.exception

/**
 * A channel is unusable without sufficient configuration information.
 */
class ConfigurationMissingException(message: String) : Exception(message)
