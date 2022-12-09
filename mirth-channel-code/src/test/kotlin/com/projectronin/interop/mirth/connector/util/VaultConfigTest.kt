package com.projectronin.interop.mirth.connector.util

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import org.springframework.vault.client.RestTemplateFactory

internal class VaultConfigTest {

    @Test
    fun endpoint() {
        val config = VaultConfig()
        val env = mockk<Environment> {
            every { getRequiredProperty("VAULT_URL") } returns "http://vault:8200"
        }
        config.setApplicationContext(
            mockk {
                every { environment } returns env
            }
        )
        val endpoint = config.vaultEndpoint()
        assertEquals("http", endpoint.scheme)
        assertEquals("vault", endpoint.host)
        assertEquals(8200, endpoint.port)
    }

    @Test
    fun token() {
        val config = VaultConfig()
        val env = mockk<Environment> {
            every { getRequiredProperty("VAULT_ROLE_ID") } returns "role"
            every { getRequiredProperty("VAULT_SECRET_ID") } returns "secret"
            every { getRequiredProperty("ENVIRONMENT") } returns "dev"
        }
        config.setApplicationContext(
            mockk {
                every { environment } returns env
                every { getBean(RestTemplateFactory::class.java).create() } returns mockk()
            }
        )
        assertNotNull(config.clientAuthentication())
    }
}
