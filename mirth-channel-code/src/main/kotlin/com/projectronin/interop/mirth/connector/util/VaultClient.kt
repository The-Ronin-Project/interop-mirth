package com.projectronin.interop.mirth.connector.util

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.projectronin.interop.mirth.connector.util.VaultClient.Keys.ENGINE
import com.projectronin.interop.mirth.connector.util.VaultClient.Keys.ENV
import com.projectronin.interop.mirth.connector.util.VaultClient.Keys.ROLE_ID
import com.projectronin.interop.mirth.connector.util.VaultClient.Keys.SECRET_ID
import com.projectronin.interop.mirth.connector.util.VaultClient.Keys.URL
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import mu.KotlinLogging

/**
 * Isolated class for loading content from [HashiCorp's Vault REST API|https://www.vaultproject.io/api-docs/secret/kv/kv-v2].
 * Required Environment Setup:
 * <ul>
 *     <li>VAULT_URL - The URL of the vault instance to call.
 *         <ul>
 *             <li>Defaults to "https://vault.devops.projectronin.io:8200"</li>
 *         </ul>
 *     </li>
 *     <li>VAULT_TOKEN - The token to be used for authentication REST calls.</li>
 *     <li>VAULT_ENGINE - The name of the secret engine to read from, for example the first part of interop-proxy-server/dev.
 *         <ul>
 *             <li>Defaults to "interop-mirth-connector"</li>
 *         </ul>
 *     </li>
 *     <li>VAULT_ENV - The environment (path) from which to read, for example the second part of interop-proxy-server/dev.</li>
 * </ul>
 */
class VaultClient {
    private val logger = KotlinLogging.logger { }

    /**
     * Key values for reading system properties.
     */
    object Keys {
        const val ENV = "ENVIRONMENT"
        const val ENGINE = "VAULT_ENGINE"
        const val URL = "VAULT_URL"
        const val ROLE_ID = "VAULT_ROLE_ID"
        const val SECRET_ID = "VAULT_SECRET_ID"
    }

    // Read Vault Configuration
    private val environment: String? = System.getenv(ENV) ?: System.getProperty(ENV)
    private val engine: String = System.getenv(ENGINE) ?: System.getProperty(ENGINE, "interop-mirth-connector")
    private val vaultUrl: String =
        System.getenv(URL) ?: System.getProperty(URL, "https://vault.devops.projectronin.io:8200")
    private val vaultRoleId: String? = System.getenv(ROLE_ID) ?: System.getProperty(ROLE_ID)
    private val vaultSecretId: String? = System.getenv(SECRET_ID) ?: System.getProperty(SECRET_ID)

    /**
     * Reads the full key/value map from the configured Vault instance.
     */
    suspend fun readConfig(): Map<String, String> {
        if (environment == null || vaultRoleId == null || vaultSecretId == null) {
            logger.info { "Vault not configured, only local environment will be accessible" }
            return emptyMap()
        }

        val vaultToken = retrieveAuthToken() ?: return emptyMap()

        logger.info { "Reading configuration from Vault" }
        val response = runCatching {
            HttpUtil.httpClient.get("$vaultUrl/v1/$engine/data/$environment") {
                headers {
                    append("X-Vault-Token", vaultToken)
                }
            }.body<VaultResponse>()
        }.onSuccess { logger.info { "Configured Vault successfully accessed" } }
            .onFailure { logger.warn { "Configured Vault could not be accessed: $it" } }.getOrNull()

        return response?.data?.data ?: emptyMap()
    }

    /**
     * Function for retrieving a Vault Token for authentication.
     */
    private suspend fun retrieveAuthToken(): String? {
        val authResponse = runCatching {
            HttpUtil.httpClient.post("$vaultUrl/v1/auth/approle-mirth-$environment/login") {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(AuthRequest(vaultRoleId, vaultSecretId))
            }.body<AuthResponse>()
        }.onSuccess { logger.info { "Vault authentication successful" } }
            .onFailure { logger.warn { "Vault authentication failed, no configuration will be accessible from vault: $it" } }
            .getOrNull() ?: return null

        return authResponse.auth.clientToken
    }
}

/**
 * Data class for handling responses from vault.
 */
data class VaultResponse(val data: KVData)

/**
 * Data class for handling inner data from vault.
 */
data class KVData(val data: Map<String, String>)

/**
 * Data class for handling the Authentication request
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AuthRequest(val roleId: String?, val secretId: String?)

/**
 * Data class for handling Vault Auth Response
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AuthResponse(val requestId: String, val auth: AuthStructure)

/**
 * Data class for containing the vault [clientToken] and [entityId] returned.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AuthStructure(val clientToken: String, val entityId: String)
