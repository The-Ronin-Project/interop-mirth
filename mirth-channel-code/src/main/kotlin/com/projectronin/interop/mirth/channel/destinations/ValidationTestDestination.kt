package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.interop.mirth.channel.ValidationTest
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ValidationTestDestination(
    val httpClient: HttpClient,
    @Value("\${aidbox.url}")
    val aidboxURLRest: String,
    val authenticationBroker: AidboxAuthenticationBroker
) : TenantlessDestinationService() {

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val aidbox = AidboxUtil(httpClient, aidboxURLRest, authenticationBroker)
        val mockEHR = ValidationTest.MockEHRUtil(httpClient, sourceMap["MockEHRURL"].toString())
        val initialResources = sourceMap[MirthKey.FHIR_ID_LIST.code] as List<String>

        val failed = mutableListOf<String>()
        val success = mutableListOf<String>()
        val maxRetries = 2
        runBlocking {
            for (retryCount in 1..maxRetries) {
                delay(20000) // Wait for 20 seconds before retrying
                val resourcesToCheck =
                    if (failed.isNotEmpty()) failed.toList() else initialResources.filterNot { it.contains("Binary") } // Only check the failed resources, if any
                failed.clear()
                resourcesToCheck.forEach { resource ->
                    when {
                        resource.contains("PatientOnboardFlag") -> {
                            val flag = runCatching {
                                mockEHR.search(
                                    "Flag",
                                    mapOf("patient" to resource.split("/").last())
                                ).entry.first().resource
                            }.getOrNull()
                            if (flag != null) {
                                success.add(resource)
                                mockEHR.deleteResource("Flag/${flag.id!!.value}")
                            } else {
                                failed.add(resource)
                            }
                        }
                        aidbox.doesResourceExist(resource, tenantMnemonic) -> success.add(resource)
                        else -> failed.add(resource)
                    }
                }
                if (failed.isEmpty()) {
                    break // Exit the loop if all resources have been deleted successfully
                }
            }
        }
        // delete all from mockEHR
        for (resource in initialResources) {
            if (resource.contains("PatientOnboardFlag")) continue
            mockEHR.deleteResource(resource)
        }

        // delete loaded resources from Aidbox
        for (resource in success) {
            if (resource.contains("PatientOnboardFlag")) continue
            aidbox.deleteResource(resource, tenantMnemonic)
        }

        return if (failed.isNotEmpty()) {
            MirthResponse(
                MirthResponseStatus.ERROR,
                "Some resources were not found in Aidbox: $failed. \nSuccessful resources: $success"
            )
        } else {
            MirthResponse(
                MirthResponseStatus.SENT,
                "Successful resources: $success"
            )
        }
    }

    class AidboxUtil(
        private val httpClient: HttpClient,
        url: String,
        private val authenticationBroker: AidboxAuthenticationBroker
    ) {
        private val BASE_URL = url

        private val FHIR_URL = "$BASE_URL/fhir"
        val RESOURCES_FORMAT = "$FHIR_URL/%s"

        private fun getAuthorizationHeader(): String {
            val authentication = authenticationBroker.getAuthentication()
            return "${authentication.tokenType} ${authentication.accessToken}"
        }

        fun deleteResource(resourceReference: String, tenantMnemonic: String) = runBlocking {
            val url = RESOURCES_FORMAT.format(createAidboxResourceReference(resourceReference, tenantMnemonic))
            httpClient.delete(url) {
                headers {
                    append(HttpHeaders.Authorization, getAuthorizationHeader())
                }
            }
        }

        fun doesResourceExist(resourceReference: String, tenantMnemonic: String): Boolean = runBlocking {
            val url = RESOURCES_FORMAT.format(createAidboxResourceReference(resourceReference, tenantMnemonic))
            try {
                httpClient.get(url) {
                    headers {
                        append(HttpHeaders.Authorization, getAuthorizationHeader())
                    }
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                }.status.isSuccess()
            } catch (e: Exception) {
                false
            }
        }

        private fun createAidboxResourceReference(resourceReference: String, tenantMnemonic: String): String {
            val split = resourceReference.split("/")
            return "${split.first()}/$tenantMnemonic-${split.last()}"
        }
    }
}
