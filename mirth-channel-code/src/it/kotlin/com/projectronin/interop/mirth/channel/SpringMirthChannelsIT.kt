package com.projectronin.interop.mirth.channel

import com.google.common.reflect.ClassPath
import com.projectronin.interop.mirth.channel.base.MirthSource
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.junitpioneer.jupiter.SetEnvironmentVariable.SetEnvironmentVariables
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.functions

/**
 * This test provides a quick mechanism for developers to determine if the Spring wiring is correct. The Environment
 * Variables below are as fake as reasonable since the test is only ensuring bean creation and not processing.
 *
 * As of Dec 14, 2022, this test is not run by any GitHub Actions and simply an assistant for local development to ensure
 * proper bean creation.
 */
@SetEnvironmentVariables(
    value = [
        SetEnvironmentVariable(key = "ENVIRONMENT", value = "local"),
        SetEnvironmentVariable(key = "VAULT_URL", value = "http://localhost/vault"),
        SetEnvironmentVariable(key = "VAULT_ROLE_ID", value = "vaultRole"),
        SetEnvironmentVariable(key = "VAULT_SECRET_ID", value = "vaultSecret"),
        SetEnvironmentVariable(key = "TENANT_DB_URL", value = "jdbc:tc:mysql:8.0://localhost:3306/databasename"),
        SetEnvironmentVariable(key = "QUEUE_DB_URL", value = "jdbc:tc:mysql:8.0://localhost:3306/databasename"),
        SetEnvironmentVariable(key = "EHRDA_URL", value = "http://localhost/ehrda"),
        SetEnvironmentVariable(key = "EHRDA_AUTH_TOKEN_URL", value = "http://mock-oauth2:8080/ehr/token"),
        SetEnvironmentVariable(key = "EHRDA_AUTH_AUDIENCE", value = "https://ehr.dev.projectronin.io"),
        SetEnvironmentVariable(key = "EHRDA_AUTH_CLIENT_ID", value = "id"),
        SetEnvironmentVariable(key = "EHRDA_AUTH_CLIENT_SECRET", value = "secret"),
        SetEnvironmentVariable(key = "EHRDA_AUTH_AUTH0", value = "false"),
        SetEnvironmentVariable(key = "VALIDATION_AUTH_TOKEN_URL", value = "http://localhost/auth"),
        SetEnvironmentVariable(key = "VALIDATION_AUTH_AUDIENCE", value = "audience"),
        SetEnvironmentVariable(key = "VALIDATION_AUTH_CLIENT_ID", value = "clientId"),
        SetEnvironmentVariable(key = "VALIDATION_AUTH_CLIENT_SECRET", value = "secret"),
        SetEnvironmentVariable(key = "VALIDATION_SERVER_URL", value = "http://localhost/validation"),
        SetEnvironmentVariable(key = "AIDBOX_URL", value = "http://localhost/aidbox"),
        SetEnvironmentVariable(key = "OCI_TENANCY_OCID", value = "tenancyOcid"),
        SetEnvironmentVariable(key = "OCI_USER_OCID", value = "userOcid"),
        SetEnvironmentVariable(key = "OCI_FINGERPRINT", value = "fingerprint"),
        SetEnvironmentVariable(key = "OCI_PRIVATE_KEY_BASE64", value = "privatekey"),
        SetEnvironmentVariable(key = "OCI_NAMESPACE", value = "namespace"),
        SetEnvironmentVariable(key = "OCI_CONCEPTMAP_BUCKET_NAME", value = "cm"),
        SetEnvironmentVariable(key = "OCI_PUBLISH_BUCKET_NAME", value = "pub"),
        SetEnvironmentVariable(key = "OCI_REGION", value = "fake-region"),
        SetEnvironmentVariable(key = "KAFKA_CLOUD_VENDOR", value = "vendor"),
        SetEnvironmentVariable(key = "KAFKA_CLOUD_REGION", value = "region"),
        SetEnvironmentVariable(key = "KAFKA_BOOTSTRAP_SERVERS", value = "servers"),
        SetEnvironmentVariable(key = "KAFKA_PUBLISH_SOURCE", value = "source"),
        SetEnvironmentVariable(key = "KAFKA_RETRIEVE_GROUPID", value = "group")
    ]
)
class SpringMirthChannelsIT {
    // This test requires a real DB to be stood up, so the initial test will take about 30s.
    @Autowired
    private lateinit var ehrDatasource: DataSource

    @Test
    fun `can create all ChannelServices`() {
        ClassPath.from(this.javaClass.classLoader).getTopLevelClasses("com.projectronin.interop.mirth.channel")
            .map { Class.forName(it.name) }.filter { MirthSource::class.java.isAssignableFrom(it) }.forEach {
                val companion = it.kotlin.companionObject!!
                val create = companion.functions.find { f -> f.name == "create" }!!
                val channel = create.call(companion.objectInstance)
                assertNotNull(channel)
                assertInstanceOf(it, channel)

                println("Successfully created ${it.simpleName}")
            }
    }

    @Test
    fun `can create KafkaPatientQueue`() {
        val channel = KafkaPatientQueue.create()
        assertNotNull(channel)
        assertInstanceOf(KafkaPatientQueue::class.java, channel)
    }
}
