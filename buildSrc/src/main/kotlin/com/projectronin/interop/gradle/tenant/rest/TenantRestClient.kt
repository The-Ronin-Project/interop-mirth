package com.projectronin.interop.gradle.tenant.rest

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.gradle.mirth.TenantConfigAuthExtension
import com.projectronin.interop.gradle.tenant.rest.model.MirthTenantConfig
import com.projectronin.interop.gradle.tenant.rest.model.TenantServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking

/**
 * Rest client capable of accessing Mirth APIs.
 */
class TenantRestClient(private val httpClient: HttpClient) {
    companion object {
        private const val BASE_URL = "http://localhost:8082"

        private const val MIRTH_TENANT_CONFIG_FORMAT = "$BASE_URL/tenants/%s/mirth-config"
        private const val TENANT_SERVER_FORMAT = "$BASE_URL/tenants/%s/tenant-server"

        /**
         * Creates a TenantRestClient for the [auth] settings.
         */
        fun createClient(auth: TenantConfigAuthExtension) =
            TenantRestClient(
                HttpClient(OkHttp) {
                    // Setup JSON
                    install(ContentNegotiation) {
                        jackson {
                            JacksonManager.setUpMapper(this)
                        }
                    }

                    // Enable logging.
                    install(Logging) {
                        level = LogLevel.ALL
                    }

                    // We probably should build a real Health Check, but this helps gracefully handle an edge-case where
                    // the Proxy Server isn't quite responding to requests yet.
                    install(HttpRequestRetry) {
                        retryOnServerErrors(maxRetries = 5)
                        exponentialDelay()
                    }

                    auth0ClientCredentials {
                        tokenUrl = auth.tokenUrl
                        clientId = System.getenv(auth.clientIdKey)
                            ?: throw IllegalStateException("Unable to find environment variable for ${auth.clientIdKey}")
                        clientSecret = System.getenv(auth.clientSecretKey)
                            ?: throw IllegalStateException("Unable to find environment variable for ${auth.clientSecretKey}")
                        audience = auth.audience
                    }
                },
            )
    }

    /**
     * GETs the current [MirthTenantConfig] for the [tenantMnemonic]. Returns null if no config currently exists.
     */
    fun getMirthTenantConfig(tenantMnemonic: String): MirthTenantConfig? =
        runBlocking {
            val url = MIRTH_TENANT_CONFIG_FORMAT.format(tenantMnemonic)
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                response.body<MirthTenantConfig>()
            } else if (response.status == HttpStatusCode.NotFound) {
                null
            } else {
                throw RuntimeException(response.status.toString())
            }
        }

    /**
     * POSTs the [mirthTenantConfig] for the [tenantMnemonic]. If this is an update to a current config, you should use [putMirthTenantConfig] instead.
     */
    fun postMirthTenantConfig(
        tenantMnemonic: String,
        mirthTenantConfig: MirthTenantConfig,
    ): HttpStatusCode =
        runBlocking {
            val url = MIRTH_TENANT_CONFIG_FORMAT.format(tenantMnemonic)
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mirthTenantConfig)
            }.status
        }

    /**
     * PUTs the [mirthTenantConfig] for the [tenantMnemonic]. If this is a new config for a tenant without one, you should use [postMirthTenantConfig] instead.
     */
    fun putMirthTenantConfig(
        tenantMnemonic: String,
        mirthTenantConfig: MirthTenantConfig,
    ): HttpStatusCode =
        runBlocking {
            val url = MIRTH_TENANT_CONFIG_FORMAT.format(tenantMnemonic)
            httpClient.put(url) {
                contentType(ContentType.Application.Json)
                setBody(mirthTenantConfig)
            }.status
        }

    fun getTenantServers(tenantMnemonic: String): List<TenantServer>? =
        runBlocking {
            val url = TENANT_SERVER_FORMAT.format(tenantMnemonic)
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                response.body<List<TenantServer>>()
            } else if (response.status == HttpStatusCode.NotFound) {
                null
            } else {
                throw RuntimeException(response.status.toString())
            }
        }

    /**
     * POSTs the [mirthTenantConfig] for the [tenantMnemonic]. If this is an update to a current config, you should use [putMirthTenantConfig] instead.
     */
    fun postTenantServer(
        tenantMnemonic: String,
        tenantServer: TenantServer,
    ): HttpStatusCode =
        runBlocking {
            val url = TENANT_SERVER_FORMAT.format(tenantMnemonic)
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(tenantServer)
            }.status
        }

    /**
     * PUTs the [mirthTenantConfig] for the [tenantMnemonic]. If this is a new config for a tenant without one, you should use [postMirthTenantConfig] instead.
     */
    fun putTenantServer(
        tenantMnemonic: String,
        tenantServer: TenantServer,
    ): HttpStatusCode =
        runBlocking {
            val url = TENANT_SERVER_FORMAT.format(tenantMnemonic)
            httpClient.put(url) {
                contentType(ContentType.Application.Json)
                setBody(tenantServer)
            }.status
        }
}
