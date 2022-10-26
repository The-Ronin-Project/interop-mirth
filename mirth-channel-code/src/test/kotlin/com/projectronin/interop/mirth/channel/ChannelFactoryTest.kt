package com.projectronin.interop.mirth.channel

import com.github.database.rider.core.api.connection.ConnectionHolder
import com.github.database.rider.core.api.dataset.DataSet
import com.projectronin.interop.common.test.database.dbrider.DBRiderConnection
import com.projectronin.interop.common.test.database.liquibase.LiquibaseTest
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.util.EnvironmentReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@LiquibaseTest(changeLog = "changelog/db.changelog-test.yaml")
@DataSet(value = ["EHRTenants.yaml"], cleanAfter = true)
class ChannelFactoryTest {
    @DBRiderConnection
    lateinit var connectionHolder: ConnectionHolder

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            mockkObject(EnvironmentReader)
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

    @Test
    fun `can create for service with only one, valid constructor`() {
        val service = ValidConstructorChannelService.create()

        assertNotNull(service)
        assertInstanceOf(ValidConstructorChannelService::class.java, service)
    }

    @Test
    fun `can create for service with multiple constructors, including one valid one`() {
        val service = MultipleConstructorChannelService.create()

        assertNotNull(service)
        assertInstanceOf(MultipleConstructorChannelService::class.java, service)
    }

    @Test
    fun `fails for service with empty constructor`() {
        val exception = assertThrows<NoSuchMethodException> {
            NoConstructorChannelService.create()
        }
        assertEquals(
            "com.projectronin.interop.mirth.channel.NoConstructorChannelService.<init>(com.projectronin.interop.mirth.connector.ServiceFactory)",
            exception.message
        )
    }

    @Test
    fun `fails for service with different constructor param`() {
        val exception = assertThrows<NoSuchMethodException> {
            DifferentConstructorParamChannelService.create()
        }
        assertEquals(
            "com.projectronin.interop.mirth.channel.DifferentConstructorParamChannelService.<init>(com.projectronin.interop.mirth.connector.ServiceFactory)",
            exception.message
        )
    }

    @Test
    fun `fails for service with too many constructor params`() {
        val exception = assertThrows<NoSuchMethodException> {
            TooManyConstructorParamsChannelService.create()
        }
        assertEquals(
            "com.projectronin.interop.mirth.channel.TooManyConstructorParamsChannelService.<init>(com.projectronin.interop.mirth.connector.ServiceFactory)",
            exception.message
        )
    }
}

internal class ValidConstructorChannelService(serviceFactory: ServiceFactory) : TestChannelService(serviceFactory) {
    companion object : ChannelFactory<ValidConstructorChannelService>()
}

internal class MultipleConstructorChannelService(serviceFactory: ServiceFactory) : TestChannelService(serviceFactory) {
    companion object : ChannelFactory<MultipleConstructorChannelService>()

    constructor(name: String, serviceFactory: ServiceFactory) : this(serviceFactory)
}

internal class NoConstructorChannelService() : TestChannelService(mockk()) {
    companion object : ChannelFactory<NoConstructorChannelService>()
}

internal class DifferentConstructorParamChannelService(name: String) : TestChannelService(mockk()) {
    companion object : ChannelFactory<DifferentConstructorParamChannelService>()
}

internal class TooManyConstructorParamsChannelService(serviceFactory: ServiceFactory, name: String) :
    TestChannelService(serviceFactory) {
    companion object : ChannelFactory<TooManyConstructorParamsChannelService>()
}

abstract class TestChannelService(serviceFactory: ServiceFactory) : ChannelService(serviceFactory) {
    override val destinations: Map<String, DestinationService>
        get() = TODO("Not yet implemented")

    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        TODO("Not yet implemented")
    }

    override val rootName: String
        get() = TODO("Not yet implemented")
}
