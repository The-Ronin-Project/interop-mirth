package com.projectronin.interop.mirth.spring

import org.springframework.context.annotation.Configuration
import org.springframework.vault.authentication.AppRoleAuthentication
import org.springframework.vault.authentication.AppRoleAuthenticationOptions
import org.springframework.vault.authentication.ClientAuthentication
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.config.AbstractVaultConfiguration
import java.net.URI

@Configuration
class VaultConfig : AbstractVaultConfiguration() {

    override fun vaultEndpoint(): VaultEndpoint {
        val url = URI(environment.getRequiredProperty("VAULT_URL"))
        return VaultEndpoint.from(url)
    }

    override fun clientAuthentication(): ClientAuthentication {
        val role = environment.getRequiredProperty("VAULT_ROLE_ID")
        val secret = environment.getRequiredProperty("VAULT_SECRET_ID")
        val context = environment.getRequiredProperty("ENVIRONMENT")
        val options =
            AppRoleAuthenticationOptions.builder()
                .appRole("approle-mirth-$context") // not sure what this does. it should do what 'path' does, and yet
                .path("approle-mirth-$context")
                .roleId(role)
                .secretId(secret)
                .build()
        return AppRoleAuthentication(options, restOperations())
    }
}
