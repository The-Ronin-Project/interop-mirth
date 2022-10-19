package com.projectronin.interop.mirth.channels.client

import com.fasterxml.jackson.databind.JsonNode
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

object MockEHRClient {
    val logger = KotlinLogging.logger { }

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

        // Enable logging.
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    private const val BASE_URL = "http://localhost:8081"

    private const val FHIR_URL = "$BASE_URL/fhir/r4"
    const val RESOURCES_FORMAT = "$FHIR_URL/%s"
    private const val RESOURCE_FORMAT = "$RESOURCES_FORMAT/%s"

    inline fun <reified T : Resource<T>> addResource(resource: Resource<T>): String = runBlocking {
        val resourceUrl = RESOURCES_FORMAT.format(resource.resourceType)
        val response = httpClient.post(resourceUrl) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(resource)
        }
        val location = response.headers["Content-Location"]
        logger.warn { "$location" }
        location!!.removePrefix("$resourceUrl/")
    }

    fun deleteResource(resourceType: String, id: String) = runBlocking {
        val url = RESOURCE_FORMAT.format(resourceType, id)
        httpClient.delete(url)
    }

    fun deleteAllResources(resourceType: String) = runBlocking {
        val resources = getAllResources(resourceType)
        KotlinLogging.logger { }.warn { resources }
        resources.get("entry")?.forEach {
            val resourceId = it.get("resource").get("id").asText()
            deleteResource(resourceType, resourceId)
        }
    }

    fun getAllResources(resourceType: String): JsonNode = runBlocking {
        val url = RESOURCES_FORMAT.format(resourceType)
        httpClient.get(url) {
        }.body()
    }
    fun getPlainBinary(fhirId: String): String = runBlocking {
        val url = RESOURCES_FORMAT.format("Binary") + "/$fhirId"
        httpClient.get(url) {
        }.body()
    }
}
