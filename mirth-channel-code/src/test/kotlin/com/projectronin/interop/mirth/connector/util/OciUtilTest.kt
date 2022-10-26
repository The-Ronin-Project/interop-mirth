package com.projectronin.interop.mirth.connector.util

import io.mockk.every
import io.mockk.mockkObject
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class OciUtilTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            mockkObject(EnvironmentReader)
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

    @Test
    fun `can create datalakePublishService`() {
        val datalakePublishService = OciUtil.datalakePublishService
    }

    @Test
    fun `can create conceptMapClient`() {
        val conceptMapClient = OciUtil.conceptMapClient
    }
}
