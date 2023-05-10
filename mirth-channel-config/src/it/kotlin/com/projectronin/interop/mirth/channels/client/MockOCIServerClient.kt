package com.projectronin.interop.mirth.channels.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.mirth.channels.testTenant
import org.mockserver.client.MockServerClient
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType
import org.mockserver.verify.VerificationTimes
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

object MockOCIServerClient {
    val client = MockServerClient("localhost", 1081)
    private const val NAMESPACE = "namespace"
    private const val PUBLISH_BUCKET = "publish-bucket"
    private const val PUBLISH_OBJECT_PREFIX = "ehr%2F"
    private const val PUBLISH_PATH = "/n/.*/b/.*/o/$PUBLISH_OBJECT_PREFIX.*"

    fun verify(numTimes: Int = 1) {
        client.verify(
            request()
                .withPath(PUBLISH_PATH)
                .withMethod("PUT"),
            VerificationTimes.exactly(numTimes)
        )
    }

    fun createExpectations(resourceType: String, resourceFhirID: String, tenant: String = testTenant) {
        val objectName = getR4Name(resourceType, resourceFhirID, tenant)
        setPutExpectation(objectName)
    }

    fun createExpectations(resourceIdsByType: Map<String, List<String>>, tenant: String = testTenant) {
        resourceIdsByType.entries.forEach { entry ->
            entry.value.forEach { createExpectations(entry.key, it, tenant) }
        }
    }

    fun getLastPublishPutBody(): String = getAllPublishPutsBody().last()

    fun getAllPublishPutsBody(): List<String> =
        client.retrieveRecordedRequests(
            request().withPath(PUBLISH_PATH).withMethod("PUT")
        ).map { String(it.body.rawBytes) }

    fun getAllPublishPutsAsResources(): List<Resource<*>> {
        val resourceStrings = getAllPublishPutsBody()
        // gotta be a better way to do this
        return resourceStrings.map {
            when {
                it.contains("\"resourceType\":\"Appointment\"") -> {
                    JacksonUtil.readJsonObject(it, Appointment::class)
                }

                it.contains("\"resourceType\":\"Condition\"") -> {
                    JacksonUtil.readJsonObject(it, Condition::class)
                }

                it.contains("\"resourceType\":\"Location\"") -> {
                    JacksonUtil.readJsonObject(it, Location::class)
                }

                it.contains("\"resourceType\":\"Observation\"") -> {
                    JacksonUtil.readJsonObject(it, Observation::class)
                }

                it.contains("\"resourceType\":\"Patient\"") -> {
                    JacksonUtil.readJsonObject(it, Patient::class)
                }

                it.contains("\"resourceType\":\"Practitioner\"") -> {
                    JacksonUtil.readJsonObject(it, Practitioner::class)
                }

                it.contains("\"resourceType\":\"PractitionerRole\"") -> {
                    JacksonUtil.readJsonObject(it, PractitionerRole::class)
                }

                else -> {
                    throw IllegalStateException("UnknownResourceType")
                }
            }
        }
    }

    private fun getR4Name(resourceType: String, resourceFhirID: String, tenant: String = testTenant): String {
        // Mirth's server clock is in UTC, so match that
        val date = ISO_LOCAL_DATE.withZone(ZoneId.from(UTC)).format(Instant.now()) // lol Java dates, graceful as always
        // .localize requires an actual Tenant object,
        // which would require more overhead than is worth it for this test
        val udpID = "$tenant-$resourceFhirID"

        // We really should use OCI's ParamEncoder.encode, but that adds a huge demendency on the OCI SDK for one call
        // so this is a hacky version of that
        return "ehr/${resourceType.lowercase()}/fhir_tenant_id=$tenant/_date=$date/$udpID.json".replace("/", "%2F")
    }

    private fun setPutExpectation(objectName: String) {
        client.`when`(
            request()
                .withMethod("PUT")
                .withPath(getObjectPath(objectName)),
            Times.exactly(1)
        ).respond(
            HttpResponse.response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
        )
    }

    fun setSekiExpectation(tenantId: String) {
        client.clear(
            request()
                .withMethod("GET")
                .withPath("/seki/session/validate")
        )
        val response = AuthResponse(User(tenantId), UserSession("2030-03-14T21:16:05"))
        client.`when`(
            request()
                .withMethod("GET")
                .withPath("/seki/session/validate"),
            Times.exactly(1)
        ).respond(
            HttpResponse.response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(JacksonUtil.writeJsonValue(response))
        )
    }

    private fun getObjectPath(objectName: String) = "/n/$NAMESPACE/b/$PUBLISH_BUCKET/o/$objectName"
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AuthResponse(
    val user: User,
    val userSession: UserSession
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonIgnoreProperties(ignoreUnknown = true)
data class User(
    val tenantId: String?
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserSession(
    val expiresAt: String?
)
