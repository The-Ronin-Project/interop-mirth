package com.projectronin.interop.mirth.channels.client

import com.fasterxml.jackson.databind.JsonNode
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
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ProxyClient {
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

    private const val GRAPHQL_URL = "$BASE_URL/graphql"

    fun getAppointmentsByMRN(mrn: String, tenantMnemonic: String, startDate: LocalDate, endDate: LocalDate): JsonNode =
        runBlocking {
            val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
            val response = httpClient.post(GRAPHQL_URL) {
                headers["Content-type"] = "application/graphql"
                accept(ContentType.Application.Any)
                setBody(
                    this::class.java.getResource("/ProxyAppointmentsMRNQuery.graphql")!!.readText()
                        .replace("__START_DATE__", startDate.format(formatter))
                        .replace("__END_DATE__", endDate.format(formatter))
                        .replace("__MRN__", mrn)
                        .replace("__TENANT__", tenantMnemonic)
                )
            }
            response.body()
        }
}
