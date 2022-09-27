package com.projectronin.interop.tenant.config.exception

/**
 * Being unable to publish resources implies an issue with the connection to or configuration of Aidbox.
 */
class ResourcesNotPublishedException(message: String) :
    Exception(message)
