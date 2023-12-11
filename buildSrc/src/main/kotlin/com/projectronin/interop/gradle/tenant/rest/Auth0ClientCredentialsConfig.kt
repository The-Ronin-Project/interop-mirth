package com.projectronin.interop.gradle.tenant.rest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson

// This entire file is stolen (and ported to ktor 2.x) from ronin-common's http-client and updated to the latest ktor.

/**
 * Configuration for the Auth0 client credentials flow.
 */
class Auth0ClientCredentialsConfig {
    /**
     * The URL for requesting an access token. Note that the full endpoint must be specified and not just the domain.
     * For example, <code>https://<something>.auth0.com/oauth/token</code>.
     *
     * Required
     */
    var tokenUrl: String? = null

    /**
     * The client id for the application requesting the access token
     *
     * Required
     */
    var clientId: String? = null

    /**
     * The client secret for the application requesting the access token
     *
     * Required
     */
    var clientSecret: String? = null

    /**
     * The audience for the access token being requested. This should be documented by the service being called, but in
     * general it's the root url for the service (e.g.
     * <code>https://dev.projectronin.io/whatever</code> for the <em>whatever</em> service)
     *
     * Optional
     */
    var audience: String? = null
}

internal data class Auth0ClientCredentialsResponse(
    val accessToken: String,
    val expiresIn: Int,
    val tokenType: String,
)

internal fun Auth0ClientCredentialsConfig.validateConfig() {
    when {
        tokenUrl.isNullOrBlank() -> throw IllegalArgumentException("tokenUrl is required")
        clientId.isNullOrBlank() -> throw IllegalArgumentException("clientId is required")
        clientSecret.isNullOrBlank() -> throw IllegalArgumentException("clientSecret is required")
        // audience is allowed to be null/empty
    }
}

/**
 * Sets up an HttpClient instance to use the Auth0 Client Credentials (i.e. machine to machine) flow for authentication
 */
fun HttpClientConfig<*>.auth0ClientCredentials(block: Auth0ClientCredentialsConfig.() -> Unit) {
    val config = Auth0ClientCredentialsConfig().apply(block)

    config.validateConfig()

    val auth0Client =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                jackson {
                    propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }

    val getToken =
        suspend {
            val token =
                auth0Client.post(config.tokenUrl!!) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(
                        mapOf(
                            "client_id" to config.clientId,
                            "client_secret" to config.clientSecret,
                            "audience" to config.audience,
                            "grant_type" to "client_credentials",
                        ),
                    )
                }.body<Auth0ClientCredentialsResponse>()

            BearerTokens(
                accessToken = token.accessToken,
                refreshToken = "NO_REFRESH_TOKEN",
            )
        }

    install(Auth) {
        bearer {
            loadTokens(getToken)
            // since we have no refresh token, just make a fresh request
            refreshTokens { getToken() }
            // Ktor waits for a 401 by default before sending along auth, but we're presumably using this client
            // on URLs we know require authentication, so we might as well skip that first unauthenticated request
            sendWithoutRequest { true }
        }
    }
}
