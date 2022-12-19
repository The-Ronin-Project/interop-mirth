package com.projectronin.interop.mirth.service

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.data.TenantServerDAO
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TenantConfigurationServiceTest {
    lateinit var mirthTenantConfigDAO: MirthTenantConfigDAO
    lateinit var tenantServerDAO: TenantServerDAO
    lateinit var service: TenantConfigurationService

    @BeforeEach
    fun setupEnvironment() {
        mirthTenantConfigDAO = mockk()
        tenantServerDAO = mockk()
        service = TenantConfigurationService(mirthTenantConfigDAO, tenantServerDAO)
    }

    @Test
    fun `can find values when they exist`() {
        every { mirthTenantConfigDAO.getByTenantMnemonic("mdaoc") } returns mockk {
            every { locationIds } returns "12,12312"
        }
        val locations = service.getLocationIDsByTenant("mdaoc")
        assertEquals(2, locations.size)
    }

    @Test
    fun `throws error when non-existent`() {
        every { mirthTenantConfigDAO.getByTenantMnemonic("fake") } returns null
        assertThrows<IllegalArgumentException> {
            service.getLocationIDsByTenant("fake")
        }
    }

    @Test
    fun `can find mdm info`() {
        every { tenantServerDAO.getTenantServers("mdaoc", MessageType.MDM) } returns listOf(
            mockk {
                every { address } returns "ProjectRonin.biz.gov.co.uk.com"
                every { port } returns 10
            }
        )
        val mdmInfo = service.getMDMInfo("mdaoc")
        assertEquals("ProjectRonin.biz.gov.co.uk.com", mdmInfo?.first)
        assertEquals(10, mdmInfo?.second)
    }
}
