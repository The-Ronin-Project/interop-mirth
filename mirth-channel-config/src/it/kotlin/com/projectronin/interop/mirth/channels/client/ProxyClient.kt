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
import io.ktor.http.contentType
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

    fun getAppointmentsByMRN(mrn: String, tenantMnemonic: String, startDate: LocalDate, endDate: LocalDate): JsonNode {
        val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
        return callGraphQLProxy(
            query = this::class.java.getResource("/ProxyAppointmentsMRNQuery.graphql")!!.readText(),
            variables = mapOf(
                "startDate" to startDate.format(formatter),
                "endDate" to endDate.format(formatter),
                "mrn" to mrn,
                "tenantId" to tenantMnemonic
            ),
            tenantMnemonic
        )
    }

    fun getPractitionerByFHIRId(fhirId: String, tenantMnemonic: String): JsonNode = callGraphQLProxy(
        query = this::class.java.getResource("/ProxyPractitionerByIdQuery.graphql")!!.readText(),
        variables = mapOf("tenantId" to tenantMnemonic, "fhirId" to fhirId),
        tenantMnemonic
    )

    fun sendNote(noteInput: Map<String, Any>, tenantMnemonic: String): JsonNode = callGraphQLProxy(
        query = this::class.java.getResource("/ProxySendNoteMutation.graphql")!!.readText(),
        variables = mapOf("noteInput" to noteInput, "tenantId" to tenantMnemonic),
        tenantMnemonic
    )

    fun getConditionsByPatient(tenantMnemonic: String, patientFhirId: String): JsonNode =
        callGraphQLProxy(
            query = this::class.java.getResource("/ProxyConditionsQuery.graphql")!!.readText(),
            variables = mapOf("tenantId" to tenantMnemonic, "patientFhirId" to patientFhirId),
            tenantMnemonic
        )

    fun getPatientByNameAndDob(tenantMnemonic: String, familyName: String, givenName: String, dob: String): JsonNode =
        callGraphQLProxy(
            query = this::class.java.getResource("/ProxyPatientSearchQuery.graphql")!!.readText(),
            variables = mapOf("tenantId" to tenantMnemonic, "familyName" to familyName, "givenName" to givenName, "birthdate" to dob),
            tenantMnemonic
        )

    private fun callGraphQLProxy(query: String, variables: Map<String, Any?>, tenantMnemonic: String): JsonNode = runBlocking {
        MockOCIServerClient.setSekiExpectation(tenantMnemonic)
        httpClient.post(GRAPHQL_URL) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                GraphQLPostRequest(
                    query = query,
                    variables = variables
                )
            )
        }.body()
    }
}

data class GraphQLPostRequest(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, Any?>? = null
)
