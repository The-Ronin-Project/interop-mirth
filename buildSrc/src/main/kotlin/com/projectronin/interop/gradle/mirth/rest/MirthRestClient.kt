package com.projectronin.interop.gradle.mirth.rest

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.gradle.mirth.rest.model.CodeTemplate
import com.projectronin.interop.gradle.mirth.rest.model.CodeTemplateLibraryList
import com.projectronin.interop.gradle.mirth.rest.model.DirectoryResourceList
import com.projectronin.interop.gradle.mirth.rest.model.MirthList
import com.projectronin.interop.gradle.mirth.rest.model.Preference
import com.projectronin.interop.gradle.mirth.rest.model.PreferenceWrapper
import com.projectronin.interop.gradle.mirth.rest.model.Preferences
import com.projectronin.interop.gradle.mirth.rest.model.User
import com.projectronin.interop.gradle.mirth.rest.model.UserWrapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Rest client capable of accessing Mirth APIs.
 */
class MirthRestClient {
    companion object {
        /**
         * Static instance of the Mirth client.
         */
        val client = MirthRestClient()

        private const val BASE_URL = "https://localhost:8443/api"
        private const val CHANNELS_URL = "$BASE_URL/channels"
        private const val CHANNELS_FORMAT = "$CHANNELS_URL/%s"
        private const val CHANNELS_ENABLED_FORMAT = "$CHANNELS_FORMAT/enabled/%b"
        private const val CODE_TEMPLATE_FORMAT = "$BASE_URL/codeTemplates/%s"
        private const val CODE_TEMPLATE_LIBRARY_URL = "$BASE_URL/codeTemplateLibraries"
        private const val RESOURCES_URL = "$BASE_URL/server/resources"
        private const val RESOURCES_RELOAD_FORMAT = "$RESOURCES_URL/%s/_reload"
        private const val USERS_URL = "$BASE_URL/users"
        private const val USER_FORMAT = "$USERS_URL/%s"
        private const val USER_PREFERENCES_FORMAT = "$USER_FORMAT/preferences"

        private val httpClient =
            HttpClient(CIO) {
                // Setup JSON
                install(ContentNegotiation) {
                    jackson {
                        JacksonManager.setUpMapper(this)
                    }
                }

                // Setup Auth
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials("admin", "admin")
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

                // Disable SSL
                engine {
                    https {
                        trustManager =
                            object : X509TrustManager {
                                override fun checkClientTrusted(
                                    chain: Array<out X509Certificate>?,
                                    authType: String?,
                                ) {}

                                override fun checkServerTrusted(
                                    chain: Array<out X509Certificate>?,
                                    authType: String?,
                                ) {}

                                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                            }
                    }
                }

                // Configure the default request
                defaultRequest {
                    header("X-Requested-With", "OpenAPI")
                }
            }
    }

    /**
     * GETs the User associated to the [userName]
     */
    fun getUser(userName: String): User? =
        runBlocking {
            val userUrl = USER_FORMAT.format(userName)
            httpClient.get(userUrl).body<UserWrapper>().user
        }

    /**
     * GETs the preferences for the User by the [userId]
     */
    fun getUserPreferences(userId: Int): List<Preference> =
        runBlocking {
            val userUrl = USER_PREFERENCES_FORMAT.format(userId)
            httpClient.get(userUrl).body<PreferenceWrapper>().preferences?.preferences ?: emptyList()
        }

    /**
     * PUTs the [preferences] for the [userId]
     */
    fun putUserPreferences(
        userId: Int,
        preferences: List<Preference>,
    ): HttpStatusCode =
        runBlocking {
            val preferenceUrl = USER_PREFERENCES_FORMAT.format(userId)
            httpClient.put(preferenceUrl) {
                contentType(ContentType.Application.Json)
                setBody(PreferenceWrapper(Preferences(preferences)))
            }.status
        }

    /**
     * HEAD request for resources, returning the [HttpStatusCode]
     */
    fun headResources(): HttpStatusCode =
        runBlocking {
            httpClient.head(RESOURCES_URL).status
        }

    /**
     * Retrieves the current [DirectoryResourceList].
     */
    fun getResources(): MirthList<DirectoryResourceList> =
        runBlocking {
            httpClient.get(RESOURCES_URL).body()
        }

    /**
     * PUTs the specified [DirectoryResourceList] into Mirth. Note that there is no way to add a single resource, so this is all or nothing and will override the entire resource list.
     */
    fun putResources(resources: MirthList<DirectoryResourceList>): HttpStatusCode =
        runBlocking {
            httpClient.put(RESOURCES_URL) {
                contentType(ContentType.Application.Json)
                setBody(resources)
            }.status
        }

    /**
     * Reloads the specified resource on Mirth.
     */
    fun reloadResource(resourceId: String): HttpStatusCode =
        runBlocking {
            val resourceUrl = RESOURCES_RELOAD_FORMAT.format(resourceId)
            httpClient.post(resourceUrl).status
        }

    /**
     * PUTs the specified [template] into Mirth based off its assigned ID.
     */
    fun putCodeTemplate(template: CodeTemplate): HttpStatusCode =
        runBlocking {
            val codeTemplateUrl = CODE_TEMPLATE_FORMAT.format(template.id)
            httpClient.put(codeTemplateUrl) {
                parameter("override", "true")

                contentType(ContentType.Application.Json)
                setBody(CodeTemplateWrapper(template))
            }.status
        }

    /**
     * Basic wrapper for a [CodeTemplate]
     */
    private data class CodeTemplateWrapper(
        val codeTemplate: CodeTemplate,
    )

    /**
     * PUTs the specified [libraries] into Mirth. Note that there is no way to add a single library, so this is all or nothing and will override the entire set of Code Template Libraries.
     */
    fun putCodeTemplateLibraries(libraries: MirthList<CodeTemplateLibraryList>): HttpStatusCode =
        runBlocking {
            httpClient.put(CODE_TEMPLATE_LIBRARY_URL) {
                parameter("override", "true")

                contentType(ContentType.Application.Json)
                setBody(libraries)
            }.status
        }

    /**
     * PUTs the supplied [channelXml] into Mirth associated to [channelId].
     */
    fun putChannel(
        channelId: String,
        channelXml: String,
    ): HttpStatusCode =
        runBlocking {
            val channelUrl = CHANNELS_FORMAT.format(channelId)
            httpClient.put(channelUrl) {
                parameter("override", "true")

                contentType(ContentType.Application.Xml)
                setBody(channelXml)
            }.status
        }

    /**
     * Enables the [channelId] in Mirth.
     */
    fun enableChannel(channelId: String): HttpStatusCode = setChannelEnabledStatus(channelId, true)

    /**
     * Disables the [channelId] in Mirth.
     */
    fun disableChannel(channelId: String): HttpStatusCode = setChannelEnabledStatus(channelId, false)

    /**
     * Sets the enabled status to [enabled] for [channelId].
     */
    private fun setChannelEnabledStatus(
        channelId: String,
        enabled: Boolean,
    ): HttpStatusCode =
        runBlocking {
            val enabledUrl = CHANNELS_ENABLED_FORMAT.format(channelId, enabled)
            httpClient.post(enabledUrl).status
        }
}
