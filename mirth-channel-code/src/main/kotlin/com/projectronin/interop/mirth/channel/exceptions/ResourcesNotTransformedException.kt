package com.projectronin.interop.tenant.config.exception

/**
 * Being unable to transform resources implies an issue with transformation code.
 */
class ResourcesNotTransformedException(message: String) : Exception(message)
