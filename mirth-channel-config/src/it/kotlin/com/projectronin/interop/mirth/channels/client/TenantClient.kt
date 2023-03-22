package com.projectronin.interop.mirth.channels.client

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
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
import java.time.LocalTime
import java.time.OffsetDateTime

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
            level = LogLevel.NONE
        }
    }
    private const val BASE_URL = "http://localhost:8082"
    private const val TENANT_URL = "$BASE_URL/tenants"
    private const val BASE_TENANT_URL = "$TENANT_URL/%s"
    private const val MIRTH_CONFIG_URL = "$BASE_TENANT_URL/mirth-config"

    fun getMirthConfig(tenantId: String): MirthConfig = runBlocking {
        MockOCIServerClient.setSekiExpectation(tenantId)
        val url = MIRTH_CONFIG_URL.format(tenantId)
        httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.body()
    }

    fun putMirthConfig(tenantId: String, mirthConfig: MirthConfig) = runBlocking {
        MockOCIServerClient.setSekiExpectation(tenantId)
        val url = MIRTH_CONFIG_URL.format(tenantId)
        httpClient.put(url) {
            contentType(ContentType.Application.Json)
            setBody(mirthConfig)
        }
    }

    fun putTenant(tenant: ProxyTenant) = runBlocking {
        MockOCIServerClient.setSekiExpectation(tenant.mnemonic)
        val url = BASE_TENANT_URL.format(tenant.mnemonic)
        httpClient.put(url) {
            contentType(ContentType.Application.Json)
            setBody(tenant)
        }
    }

    fun getTenant(tenantId: String): ProxyTenant = runBlocking {
        MockOCIServerClient.setSekiExpectation(tenantId)
        val url = BASE_TENANT_URL.format(tenantId)
        httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.body()
    }

    data class ProxyTenant(
        val id: Int,
        val mnemonic: String,
        val name: String,
        val availableStart: LocalTime?,
        val availableEnd: LocalTime?,
        val vendor: Vendor,
        val timezone: String
    )

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = Epic::class, name = "EPIC"),
        JsonSubTypes.Type(value = Cerner::class, name = "CERNER")
    )
    interface Vendor {
        val vendorType: VendorType
        val instanceName: String
    }

    enum class VendorType {
        EPIC,
        CERNER
    }

    @JsonTypeName("EPIC")
    data class Epic(
        val release: String,
        val serviceEndpoint: String,
        val authEndpoint: String,
        val ehrUserId: String,
        val messageType: String,
        val practitionerProviderSystem: String,
        val practitionerUserSystem: String,
        val patientMRNSystem: String,
        val patientInternalSystem: String,
        val encounterCSNSystem: String,
        val patientMRNTypeText: String,
        val hsi: String?,
        override val instanceName: String,
        val departmentInternalSystem: String
    ) : Vendor {
        override val vendorType: VendorType = VendorType.EPIC
    }

    @JsonTypeName("CERNER")
    data class Cerner(
        val serviceEndpoint: String,
        val authEndpoint: String,
        val patientMRNSystem: String,
        override val instanceName: String,
        val messagePractitioner: String,
        val messageTopic: String?,
        val messageCategory: String?,
        val messagePriority: String?
    ) : Vendor {
        override val vendorType: VendorType = VendorType.CERNER
    }

    data class MirthConfig(val locationIds: List<String>, val lastUpdated: OffsetDateTime? = null)
}
