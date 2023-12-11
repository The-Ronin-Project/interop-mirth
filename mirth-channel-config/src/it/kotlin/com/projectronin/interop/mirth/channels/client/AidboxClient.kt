package com.projectronin.interop.mirth.channels.client

import com.fasterxml.jackson.databind.JsonNode
import com.projectronin.interop.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.interop.aidbox.auth.AidboxAuthenticationService
import com.projectronin.interop.aidbox.auth.AidboxCredentials
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking

object AidboxClient {
    val httpClient = HttpClient(OkHttp) {
        // If not a successful response, Ktor will throw Exceptions
        expectSuccess = true

        // Setup JSON
        install(ContentNegotiation) {
            jackson {
                JacksonManager.setUpMapper(this)
            }
        }

        // Enable logging.
        install(Logging) {
            level = LogLevel.NONE
        }
    }

    private const val BASE_URL = "http://localhost:8888"

    private val aidboxCredentials = AidboxCredentials("client", "secret")
    private val authenticationService =
        AidboxAuthenticationService(httpClient, BASE_URL, aidboxCredentials)
    private val authenticationBroker = AidboxAuthenticationBroker(authenticationService)

    private const val FHIR_URL = "$BASE_URL/fhir"
    const val RESOURCES_FORMAT = "$FHIR_URL/%s"
    const val RESOURCE_FORMAT = "$RESOURCES_FORMAT/%s"

    const val TENANT_IDENTIFIER_FORMAT = "http://projectronin.com/id/tenantId|%s"

    fun getAuthorizationHeader(): String {
        val authentication = authenticationBroker.getAuthentication()
        return "${authentication.tokenType} ${authentication.accessToken}"
    }

    inline fun <reified T : Resource<T>> getResource(resourceType: String, resourceId: String): T =
        runBlocking {
            val url = RESOURCE_FORMAT.format(resourceType, resourceId)
            httpClient.get(url) {
                headers {
                    append(HttpHeaders.Authorization, getAuthorizationHeader())
                }
            }.body()
        }

    fun getAllResourcesForTenant(resourceType: String, tenant: String): JsonNode = runBlocking {
        val tenantIdentifier = TENANT_IDENTIFIER_FORMAT.format(tenant)
        val url = RESOURCES_FORMAT.format(resourceType)
        httpClient.get(url) {
            url {
                parameters.append("identifier", tenantIdentifier)
            }
            headers {
                append(HttpHeaders.Authorization, getAuthorizationHeader())
            }
        }.body()
    }

    inline fun <reified T : Resource<T>> addResource(resource: Resource<T>): T = runBlocking {
        val resourceUrl = RESOURCES_FORMAT.format(resource.resourceType)
        httpClient.post(resourceUrl) {
            headers {
                append(HttpHeaders.Authorization, getAuthorizationHeader())
                append("aidbox-validation-skip", "reference")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(resource)
        }.body()
    }

    fun deleteResource(resourceType: String, id: String) = runBlocking {
        val url = RESOURCE_FORMAT.format(resourceType, id)
        httpClient.delete(url) {
            headers {
                append(HttpHeaders.Authorization, getAuthorizationHeader())
            }
        }
    }

    fun deleteAllResources(resourceType: String, tenant: String) = runBlocking {
        val resources = getAllResourcesForTenant(resourceType, tenant)
        resources.get("entry").forEach {
            val resourceId = it.get("resource").get("id").asText()
            deleteResource(resourceType, resourceId)
        }
    }
}
