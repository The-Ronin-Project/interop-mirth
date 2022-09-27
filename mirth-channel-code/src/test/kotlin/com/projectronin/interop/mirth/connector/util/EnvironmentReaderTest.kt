package com.projectronin.interop.mirth.connector.util

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junitpioneer.jupiter.SetEnvironmentVariable

class EnvironmentReaderTest {

    private val mockWebServer = MockWebServer()

    @BeforeEach
    fun beforeEach() {
        val mockAuthResponse = """
            |{
            |  "request_id": "3d09b2f8-3bc6-72cb-c743-33aee6797d42",
            |  "auth": {
            |    "client_token": "token.value",
            |    "entity_id": "86f20bde-5a7f-5307-bc14-4b07ccff5074"
            |  }
            |}""".trimMargin()
        val mockResponse = """
            |{
            |   "data": {
            |       "data": {
            |           "TEST_KEY_1": "testValue1",
            |           "TEST_KEY_2": "testValue2"
            |       }
            |   }
            |}""".trimMargin()
        mockWebServer.start()
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(mockAuthResponse).setHeader("Content-Type", "application/json")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(mockResponse).setHeader("Content-Type", "application/json")
        )
        System.setProperty(VaultClient.Keys.ROLE_ID, "vault-client-role-id")
        System.setProperty(VaultClient.Keys.SECRET_ID, "vault-client-secret-id")
        System.setProperty(VaultClient.Keys.ENV, "dev")
        System.setProperty(VaultClient.Keys.URL, "http://localhost:${mockWebServer.port}/")
    }

    @AfterEach
    fun afterEach() {
        System.clearProperty(VaultClient.Keys.ENV)
        System.clearProperty(VaultClient.Keys.ENGINE)
        System.clearProperty(VaultClient.Keys.URL)
        System.clearProperty(VaultClient.Keys.ROLE_ID)
        System.clearProperty(VaultClient.Keys.SECRET_ID)
        mockWebServer.shutdown()
    }

    @Test
    fun `returns null on unknown value`() {
        val value = EnvironmentReader.read("UNKNOWN_KEY")
        assertNull(value)
    }

    @Test
    @SetEnvironmentVariable(key = "KNOWN_KEY", value = "known value")
    fun `returns value on known value`() {
        val value = EnvironmentReader.read("KNOWN_KEY")
        assertEquals("known value", value)
    }

    @Test
    fun `returns default when provided on unknown value`() {
        val value = EnvironmentReader.readWithDefault("UNKNOWN_KEY", "default")
        assertEquals("default", value)
    }

    @Test
    @SetEnvironmentVariable(key = "KNOWN_KEY", value = "known value")
    fun `returns value when provided default on known value`() {
        val value = EnvironmentReader.readWithDefault("KNOWN_KEY", "default")
        assertEquals("known value", value)
    }

    @Test
    @SetEnvironmentVariable(key = "KNOWN_KEY", value = "known value")
    fun `read required returns value on known value`() {
        val value = EnvironmentReader.readRequired("KNOWN_KEY")
        assertEquals("known value", value)
    }

    @Test
    fun `throws exception when required provided on unknown value`() {
        val exception = assertThrows<IllegalStateException> { EnvironmentReader.readRequired("UNKNOWN_KEY") }
        assertEquals("Required environment variable UNKNOWN_KEY missing.", exception.message)
    }

    @Test
    @SetEnvironmentVariable(key = "TEST_KEY_1", value = "local value")
    fun `vault overrides local values`() {
        assertEquals("testValue1", EnvironmentReader.readWithDefault("TEST_KEY_1", "default"))
        assertEquals("testValue1", EnvironmentReader.read("TEST_KEY_1"))
        assertEquals("testValue1", EnvironmentReader.readRequired("TEST_KEY_1"))
        assertEquals("testValue2", EnvironmentReader.readWithDefault("TEST_KEY_2", "default"))
        assertEquals("testValue2", EnvironmentReader.read("TEST_KEY_2"))
        assertEquals("testValue2", EnvironmentReader.readRequired("TEST_KEY_2"))
    }
}
