package com.projectronin.interop.tenant.config.exception

/**
 * Finding no resources at all implies an issue with the connection to or configuration of the tenant.
 */
class ResourcesNotFoundException(message: String) : Exception(message)
