package com.projectronin.interop.mirth.channels.client

import com.fasterxml.jackson.databind.JsonNode
import com.projectronin.interop.common.jackson.JacksonManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
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

object MirthClient {
    private val httpClient = HttpClient(CIO) {
        // If not a successful response, Ktor will throw Exceptions
        expectSuccess = true

        // Setup JSON
        install(ContentNegotiation) {
            jackson {
                JacksonManager.setUpMapper(this)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
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
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            }
        }

        // Configure the default request
        defaultRequest {
            header("X-Requested-With", "OpenAPI")
        }
    }

    private const val BASE_URL = "https://localhost:8443/api"
    private const val CHANNELS_URL = "$BASE_URL/channels"
    private const val CHANNELS_FORMAT = "$CHANNELS_URL/%s"
    private const val CHANNELS_ENABLE_FORMAT = "$CHANNELS_FORMAT/enabled/true"
    private const val CHANNELS_DEPLOY_FORMAT = "$CHANNELS_FORMAT/_deploy"
    private const val CHANNELS_START_FORMAT = "$CHANNELS_FORMAT/_start"
    private const val CHANNELS_STOP_FORMAT = "$CHANNELS_FORMAT/_stop"
    private const val CHANNELS_MESSAGES_FORMAT = "$CHANNELS_FORMAT/messages"
    private const val CHANNELS_MESSAGES_COUNT_FORMAT = "$CHANNELS_MESSAGES_FORMAT/count"
    private const val CHANNELS_MESSAGES_REMOVEALL_FORMAT = "$CHANNELS_MESSAGES_FORMAT/_removeAll"
    private const val CLEAR_STATISTICS_URL = "$CHANNELS_URL/_clearAllStatistics"

    fun putChannel(channelId: String, channelXml: String): HttpStatusCode = runBlocking {
        val channelUrl = CHANNELS_FORMAT.format(channelId)
        httpClient.put(channelUrl) {
            parameter("override", "true")

            contentType(ContentType.Application.Xml)
            setBody(channelXml)
        }.status
    }

    fun enableChannel(channelId: String) = runBlocking {
        val enableUrl = CHANNELS_ENABLE_FORMAT.format(channelId)
        httpClient.post(enableUrl)
    }

    fun deployChannel(channelId: String) = runBlocking {
        val deployUrl = CHANNELS_DEPLOY_FORMAT.format(channelId)
        httpClient.post(deployUrl) {
            url {
                parameters.append("returnErrors", "true")
            }
        }
    }

    fun startChannel(channelId: String) = runBlocking {
        val startUrl = CHANNELS_START_FORMAT.format(channelId)
        httpClient.post(startUrl) {
            url {
                parameters.append("returnErrors", "true")
            }
        }
    }

    fun stopChannel(channelId: String) = runBlocking {
        val stopUrl = CHANNELS_STOP_FORMAT.format(channelId)
        httpClient.post(stopUrl) {
            url {
                parameters.append("returnErrors", "true")
            }
        }
    }

    fun getChannelMessages(channelId: String): JsonNode = runBlocking {
        val messagesUrl = CHANNELS_MESSAGES_FORMAT.format(channelId)
        httpClient.get(messagesUrl) {
            url {
                parameters.appendAll("status", listOf("SENT", "ERROR"))
                parameters.append("includeContent", "true")

                // These are required, for some reason
                parameters.append("offset", "0")
                parameters.append("limit", "20")
            }
        }.body()
    }

    fun clearChannelMessages(channelId: String) = runBlocking {
        val clearMessagesUrl = CHANNELS_MESSAGES_REMOVEALL_FORMAT.format(channelId)
        httpClient.delete(clearMessagesUrl) {
            url {
                parameters.append("clearStatistics", "true")
                parameters.append("restartRunningChannels", "true")
            }
        }
    }

    fun getCompletedMessageCount(channelId: String): Int = runBlocking {
        val messageUrl = CHANNELS_MESSAGES_COUNT_FORMAT.format(channelId)
        val jsonNode = httpClient.get(messageUrl) {
            url {
                parameters.appendAll("status", listOf("SENT", "ERROR"))
            }
        }.body<JsonNode>()
        jsonNode.get("long").asInt()
    }

    fun clearAllStatistics() = runBlocking {
        httpClient.post(CLEAR_STATISTICS_URL)
    }
}
