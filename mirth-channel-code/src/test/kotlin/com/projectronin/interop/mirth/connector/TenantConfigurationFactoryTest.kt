package com.projectronin.interop.mirth.connector

import com.github.database.rider.core.api.connection.ConnectionHolder
import com.github.database.rider.core.api.dataset.DataSet
import com.projectronin.interop.common.test.database.dbrider.DBRiderConnection
import com.projectronin.interop.common.test.database.liquibase.LiquibaseTest
import com.projectronin.interop.mirth.connector.util.EnvironmentReader
import io.mockk.every
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@LiquibaseTest(changeLog = "changelog/db.changelog-test.yaml")
@DataSet(value = ["EHRTenants.yaml"], cleanAfter = true)
class TenantConfigurationFactoryTest {
    @DBRiderConnection
    lateinit var connectionHolder: ConnectionHolder

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupEnvironment() {
            mockkObject(EnvironmentReader)
            every { EnvironmentReader.readRequired("TENANT_DB_URL") } returns LiquibaseTest.DEFAULT_DB_URL
            every { EnvironmentReader.read("TENANT_DB_USERNAME") } returns null
            every { EnvironmentReader.read("TENANT_DB_PASSWORD") } returns null
            every { EnvironmentReader.readRequired("QUEUE_DB_URL") } returns LiquibaseTest.DEFAULT_DB_URL
            every { EnvironmentReader.read("QUEUE_DB_USERNAME") } returns null
            every { EnvironmentReader.read("QUEUE_DB_PASSWORD") } returns null
        }
    }

    @Test
    fun `service factory can be created`() {
        assertNotNull(TenantConfigurationFactoryImpl)
    }

    @Test
    fun `can find values when they exist`() {
        val locations = TenantConfigurationFactoryImpl.getLocationIDsByTenant("mdaoc")
        assertEquals(3, locations.size)
    }

    @Test
    fun `throws error when non-existent`() {
        assertThrows<IllegalArgumentException> {
            TenantConfigurationFactoryImpl.getLocationIDsByTenant("fake")
        }
    }

    @Test
    fun `can find mdm info`() {
        val mdmInfo = TenantConfigurationFactoryImpl.getMDMInfo("mdaoc")
        assertEquals("ProjectRonin.biz.gov.co.uk.com", mdmInfo?.first)
        assertEquals(10, mdmInfo?.second)
    }
}
