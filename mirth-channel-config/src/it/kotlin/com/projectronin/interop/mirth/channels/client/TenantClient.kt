package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.common.jackson.JacksonManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking

object TenantClient {
    val httpClient = HttpClient(CIO) {
        // If not a successful response, Ktor will throw Exceptions
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
        // Setup JSON
        install(ContentNegotiation) {
            jackson {
                JacksonManager.setUpMapper(this)
            }
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(
                        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                            "eyJpYXQiOjE2NTY1MTQ3NDUsImlzcyI6IlNla2kiLCJqdGkiOiIycn" +
                            "VodW9qNWtxcTRuY3U0czQwMDAwOGYiLCJzdWIiOiJiYmI2NWZmNS00" +
                            "MGQ4LTRlOGItYWQ1Ny1lODVkNzg5YzZkYmEiLCJ0ZW5hbnRpZCI6In" +
                            "JvbmluIn0.kLu2lKS16U9IQLpU2GyfoqvKGM76VblffZxPTdkfeTQ",
                        "refresh" // not sure what this does but it's required
                    )
                }
                sendWithoutRequest {
                    it.url.host == "localhost"
                }
            }
        }
        // Enable logging.
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    private const val BASE_URL = "http://localhost:8082"
    private const val TENANT_URL = "$BASE_URL/tenants"
    private const val BASE_TENANT_URL = "$TENANT_URL/%s"
    private const val MIRTH_CONFIG_URL = "$BASE_TENANT_URL/mirth-config"

    fun getMirthConfig(tenantId: String): MirthConfig = runBlocking {
        val url = MIRTH_CONFIG_URL.format(tenantId)
        httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.body()
    }

    fun putMirthConfig(tenantId: String, mirthConfig: MirthConfig) = runBlocking {
        val url = MIRTH_CONFIG_URL.format(tenantId)
        httpClient.put(url) {
            contentType(ContentType.Application.Json)
            setBody(mirthConfig)
        }
    }

    data class MirthConfig(val locationIds: List<String>)
}
