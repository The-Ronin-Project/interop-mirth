package com.projectronin.interop.mirth.channels.client

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

    fun verify(numTimes: Int = 1) {
        client.verify(
            request()
                .withPath("/n/.*/b/.*/o/.*"),
            VerificationTimes.exactly(numTimes)
        )
    }

    fun createExpectations(resourceType: String, resourceFhirID: String) {
        val objectName = getR4Name(resourceType, resourceFhirID)
        setPutExpectation(objectName)
    }

    fun createExpectations(resourceIdsByType: Map<String, List<String>>) {
        resourceIdsByType.entries.forEach { entry ->
            entry.value.forEach { createExpectations(entry.key, it) }
        }
    }

    fun getLastPutBody(): String = getAllPutsBody().first()

    fun getAllPutsBody(): List<String> =
        client.retrieveRecordedRequests(
            request().withMethod("PUT")
        ).map { String(it.body.rawBytes) }

    fun getAllPutsAsResources(): List<Resource<*>> {
        val resourceStrings = getAllPutsBody()
        // gotta be a better way to do this
        return resourceStrings.map {
            when {
                it.contains("\"resourceType\":\"Appointment\"") -> { JacksonUtil.readJsonObject(it, Appointment::class) }
                it.contains("\"resourceType\":\"Condition\"") -> { JacksonUtil.readJsonObject(it, Condition::class) }
                it.contains("\"resourceType\":\"Location\"") -> { JacksonUtil.readJsonObject(it, Location::class) }
                it.contains("\"resourceType\":\"Observation\"") -> { JacksonUtil.readJsonObject(it, Observation::class) }
                it.contains("\"resourceType\":\"Patient\"") -> { JacksonUtil.readJsonObject(it, Patient::class) }
                it.contains("\"resourceType\":\"Practitioner\"") -> { JacksonUtil.readJsonObject(it, Practitioner::class) }
                it.contains("\"resourceType\":\"PractitionerRole\"") -> { JacksonUtil.readJsonObject(it, PractitionerRole::class) }
                else -> { throw IllegalStateException("UnknownResourceType") }
            }
        }
    }

    private fun getR4Name(resourceType: String, resourceFhirID: String): String {
        // Mirth's server clock is in UTC, so match that
        val date = ISO_LOCAL_DATE.withZone(ZoneId.from(UTC)).format(Instant.now()) // lol Java dates, graceful as always
        // .localize requires an actual Tenant object,
        // which would require more overhead than is worth it for this test
        val udpID = "$testTenant-$resourceFhirID"

        // We really should use OCI's ParamEncoder.encode, but that adds a huge demendency on the OCI SDK for one call
        // so this is a hacky version of that
        return "fhir-r4/date=$date/tenant_id=$testTenant/resource_type=$resourceType/$udpID.json".replace("/", "%2F")
    }

    private fun setPutExpectation(objectName: String) {
        client.`when`(
            request()
                .withMethod("PUT")
                .withPath(getObjectPath(objectName))
        ).respond(
            HttpResponse.response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
        )
    }

    private fun getObjectPath(objectName: String) = "/n/$NAMESPACE/b/$PUBLISH_BUCKET/o/$objectName"
}
