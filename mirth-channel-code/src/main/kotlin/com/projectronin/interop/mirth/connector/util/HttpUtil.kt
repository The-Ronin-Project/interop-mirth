package com.projectronin.interop.mirth.connector.util

import com.projectronin.interop.common.http.spring.HttpSpringConfig

/**
 * Utility providing access to HTTP clients.
 */
internal object HttpUtil {
    // Though we're not using Spring, we can re-use the HttpClient provided by the common projects.
    val httpClient = HttpSpringConfig().getHttpClient()
}
