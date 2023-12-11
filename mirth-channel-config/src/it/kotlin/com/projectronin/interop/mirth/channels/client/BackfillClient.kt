package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.backfill.client.BackfillClient
import com.projectronin.interop.backfill.client.DiscoveryQueueClient
import com.projectronin.interop.backfill.client.QueueClient
import com.projectronin.interop.backfill.client.spring.BackfillClientConfig
import com.projectronin.interop.backfill.client.spring.Server
import com.projectronin.interop.common.http.auth.AuthMethod
import com.projectronin.interop.common.http.auth.AuthenticationConfig
import com.projectronin.interop.common.http.auth.Client
import com.projectronin.interop.common.http.auth.InteropAuthenticationService
import com.projectronin.interop.common.http.auth.Token
import com.projectronin.interop.common.jackson.JacksonManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.jackson.jackson

object BackfillClient {
    private val httpClient = HttpClient(OkHttp) {
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
            level = LogLevel.NONE
        }
    }
    private val authConfig = AuthenticationConfig(
        token = Token("http://localhost:8085/backfill/token"),
        audience = "https://backfill.dev.projectronin.io",
        client = Client(id = "id", secret = "secret"),
        method = AuthMethod.STANDARD
    )
    private val backFillClientConfig = BackfillClientConfig(server = Server("http://localhost:8086"))
    private val authService = InteropAuthenticationService(httpClient, authConfig)
    val backfillClient = BackfillClient(httpClient, backFillClientConfig, authService)
    val discoveryQueueClient = DiscoveryQueueClient(httpClient, backFillClientConfig, authService)
    val queueClient = QueueClient(httpClient, backFillClientConfig, authService)
}
