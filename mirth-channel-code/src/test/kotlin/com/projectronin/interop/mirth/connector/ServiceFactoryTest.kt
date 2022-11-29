package com.projectronin.interop.mirth.connector

import com.github.database.rider.core.api.connection.ConnectionHolder
import com.github.database.rider.core.api.dataset.DataSet
import com.projectronin.interop.common.test.database.dbrider.DBRiderConnection
import com.projectronin.interop.common.test.database.liquibase.LiquibaseTest
import com.projectronin.interop.mirth.connector.util.EnvironmentReader
import io.mockk.every
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@LiquibaseTest(changeLog = "changelog/db.changelog-test.yaml")
@DataSet(value = ["EHRTenants.yaml"], cleanAfter = true)
class ServiceFactoryTest {
    @DBRiderConnection
    lateinit var connectionHolder: ConnectionHolder

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            mockkObject(EnvironmentReader)
            every { EnvironmentReader.readWithDefault("TENANT_CONFIG_YAML", any()) } returns "classpath:tenants.yaml"
            every { EnvironmentReader.readRequired("AIDBOX_CLIENT_ID") } returns "root"
            every { EnvironmentReader.readRequired("AIDBOX_CLIENT_SECRET") } returns "secret"
            every { EnvironmentReader.readRequired("AIDBOX_REST_URL") } returns "http://localhost:8888"
            every { EnvironmentReader.readRequired("TENANT_DB_URL") } returns LiquibaseTest.DEFAULT_DB_URL
            every { EnvironmentReader.read("TENANT_DB_USERNAME") } returns null
            every { EnvironmentReader.read("TENANT_DB_PASSWORD") } returns null
            every { EnvironmentReader.readRequired("QUEUE_DB_URL") } returns LiquibaseTest.DEFAULT_DB_URL
            every { EnvironmentReader.read("QUEUE_DB_USERNAME") } returns null
            every { EnvironmentReader.read("QUEUE_DB_PASSWORD") } returns null
            every { EnvironmentReader.read("OCI_TENANCY_OCID") } returns "oci_tenant"
            every { EnvironmentReader.read("OCI_USER_OCID") } returns "oci_user_ocid"
            every { EnvironmentReader.read("OCI_FINGERPRINT") } returns "oci_user_fingerprint"
            every { EnvironmentReader.read("OCI_PRIVATE_KEY_BASE64") } returns "oci_key"
            every { EnvironmentReader.read("OCI_NAMESPACE") } returns "ock_bucket_namespace"
            every { EnvironmentReader.read("OCI_PUBLISH_BUCKET_NAME") } returns "oci_publish_bucket"
            every { EnvironmentReader.read("OCI_CONCEPTMAP_BUCKET_NAME") } returns "oci_infx_bucket"
            every { EnvironmentReader.read("OCI_REGION") } returns "us-phoenix-1"
        }
    }

    private val mirthServiceFactory = ServiceFactoryImpl

    @Test
    fun `factory creates VendorFactory for tenant`() {
        val tenant = mirthServiceFactory.getTenant("mdaoc")
        assertNotNull(mirthServiceFactory.vendorFactory(tenant))
    }

    @Test
    fun `factory creates VendorFactory for tenant id`() {
        assertNotNull(mirthServiceFactory.vendorFactory("mdaoc"))
    }

    @Test
    fun `factory creates PublishService`() {
        assertNotNull(mirthServiceFactory.publishService())
    }

    @Test
    fun `factory creates PractitionerService`() {
        assertNotNull(mirthServiceFactory.practitionerService())
    }

    @Test
    fun `factory creates PatientService`() {
        assertNotNull(mirthServiceFactory.patientService())
    }

    @Test
    fun `factory creates TenantConfigurationService`() {
        assertNotNull(mirthServiceFactory.tenantConfigurationFactory())
    }

    @Test
    fun `dequeue works`() {
        assertNotNull(mirthServiceFactory.queueService())
    }

    @Test
    fun `conceptMap works`() {
        assertNotNull(mirthServiceFactory.conceptMapClient())
    }
}
