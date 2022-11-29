package com.projectronin.interop.mirth.connector.util

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable
import java.net.InetAddress

class VaultClientTest {
    private val mockWebServer = MockWebServer()

    private val mockAuthResponse = """
            |{
            |  "request_id": "3d09b2f8-3bc6-72cb-c743-33aee6797d42",
            |  "lease_id": "",
            |  "renewable": false,
            |  "lease_duration": 0,
            |  "data": null,
            |  "wrap_info": null,
            |  "warnings": null,
            |  "auth": {
            |    "client_token": "token.value",
            |    "accessor": "wTTcn36CfrFEFrxRm7NypH6T",
            |    "policies": [
            |      "default",
            |      "mirth-dev"
            |    ],
            |    "token_policies": [
            |      "default",
            |      "mirth-dev"
            |    ],
            |    "metadata": {
            |      "role_name": "mirth-dev-role"
            |    },
            |    "lease_duration": 43200,
            |    "renewable": true,
            |    "entity_id": "86f20bde-5a7f-5307-bc14-4b07ccff5074",
            |    "token_type": "service",
            |    "orphan": true
            |  }
            |}""".trimMargin()

    @AfterEach
    fun afterTest() {
        System.clearProperty(VaultClient.Keys.ENV)
        System.clearProperty(VaultClient.Keys.ENGINE)
        System.clearProperty(VaultClient.Keys.URL)
        System.clearProperty(VaultClient.Keys.ROLE_ID)
        System.clearProperty(VaultClient.Keys.SECRET_ID)
        mockWebServer.shutdown()
    }

    @Test
    fun `invalid environment returns no values`() {
        System.setProperty(VaultClient.Keys.ENV, "dev")
        val value = runBlocking { VaultClient().readConfig() }
        assertTrue(value.isEmpty())
    }

    @Test
    fun `missing configuration returns no values`() {
        val value = runBlocking { VaultClient().readConfig() }
        assertTrue(value.isEmpty())
    }

    @Test
    fun `environment is required`() {
        val value = runBlocking { VaultClient().readConfig() }
        assertTrue(value.isEmpty())
    }

    @Test
    fun `role id is required`() {
        System.setProperty(VaultClient.Keys.ENV, "dev")
        System.setProperty(VaultClient.Keys.SECRET_ID, "vault-client-secret-id")
        val value = runBlocking { VaultClient().readConfig() }
        assertTrue(value.isEmpty())
    }

    @Test
    fun `secret id is required`() {
        System.setProperty(VaultClient.Keys.ENV, "dev")
        System.setProperty(VaultClient.Keys.ROLE_ID, "vault-client-role-id")
        val value = runBlocking { VaultClient().readConfig() }
        assertTrue(value.isEmpty())
    }

    @Test
    fun `key values can be loaded from vault`() {
        System.setProperty(VaultClient.Keys.ROLE_ID, "vault-client-role-id")
        System.setProperty(VaultClient.Keys.SECRET_ID, "vault-client-secret-id")
        System.setProperty(VaultClient.Keys.ENGINE, "interop-test")
        System.setProperty(VaultClient.Keys.ENV, "dev")
        System.setProperty(VaultClient.Keys.URL, "http://localhost:8083/")
        val mockResponse = """
            |{
            |   "data": {
            |       "data": {
            |           "TEST_KEY_1": "testValue1",
            |           "TEST_KEY_2": "testValue2"
            |       }
            |   }
            |}""".trimMargin()
        mockWebServer.start(8083)
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(mockAuthResponse).setHeader("Content-Type", "application/json")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(mockResponse).setHeader("Content-Type", "application/json")
        )

        val value = runBlocking { VaultClient().readConfig() }
        assertEquals(mapOf("TEST_KEY_1" to "testValue1", "TEST_KEY_2" to "testValue2"), value)
    }

    @Test
    fun `vault auth errors are handled`() {
        System.setProperty(VaultClient.Keys.ROLE_ID, "vault-client-role-id")
        System.setProperty(VaultClient.Keys.SECRET_ID, "vault-client-secret-id")
        System.setProperty(VaultClient.Keys.ENGINE, "interop-test")
        System.setProperty(VaultClient.Keys.ENV, "dev")
        System.setProperty(VaultClient.Keys.URL, "http://localhost:8083/")
        mockWebServer.start(8083)
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403)
        )

        val value = runBlocking { VaultClient().readConfig() }
        assertTrue(value.isEmpty())
    }

    @SetEnvironmentVariable(key = "VAULT_ROLE_ID", value = "vault-client-role-id")
    @SetEnvironmentVariable(key = "VAULT_SECRET_ID", value = "vault-client-secret-id")
    @SetEnvironmentVariable(key = "VAULT_ENGINE", value = "interop-test")
    @SetEnvironmentVariable(key = "ENVIRONMENT", value = "dev")
    @SetEnvironmentVariable(key = "VAULT_URL", value = "http://localhost:8083/")
    @Test
    fun `key values can be loaded from vault via environment`() {
        val mockResponse = """
            |{
            |   "data": {
            |       "data": {
            |           "TEST_KEY_1": "testValue1",
            |           "TEST_KEY_2": "testValue2"
            |       }
            |   }
            |}""".trimMargin()
        mockWebServer.start(8083)
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(mockAuthResponse).setHeader("Content-Type", "application/json")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(mockResponse).setHeader("Content-Type", "application/json")
        )

        val value = runBlocking { VaultClient().readConfig() }
        assertEquals(mapOf("TEST_KEY_1" to "testValue1", "TEST_KEY_2" to "testValue2"), value)
    }

    @SetEnvironmentVariable(key = "VAULT_ROLE_ID", value = "vault-client-role-id")
    @SetEnvironmentVariable(key = "VAULT_SECRET_ID", value = "vault-client-secret-id")
    @SetEnvironmentVariable(key = "VAULT_ENGINE", value = "interop-test")
    @SetEnvironmentVariable(key = "ENVIRONMENT", value = "dev")
    @SetEnvironmentVariable(key = "VAULT_URL", value = "http://localhost:8083/")
    @Test
    fun `server errors default to empty map`() {
        mockWebServer.start(8083)
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(mockAuthResponse).setHeader("Content-Type", "application/json")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(404).setBody("{}").setHeader("Content-Type", "application/json")
        )

        val value = runBlocking { VaultClient().readConfig() }
        assertEquals(emptyMap<String, String>(), value)
    }

    @Test
    fun `errors from vault default to empty map`() {
        System.setProperty(VaultClient.Keys.ENV, "dev")
        System.setProperty(VaultClient.Keys.URL, "http://localhost:8083/")
        mockWebServer.start(InetAddress.getLocalHost(), 8000)
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403).setHeader("Content-Type", "application/json")
        )
        val value = runBlocking { VaultClient().readConfig() }
        assertTrue(value.isEmpty())
    }
}
