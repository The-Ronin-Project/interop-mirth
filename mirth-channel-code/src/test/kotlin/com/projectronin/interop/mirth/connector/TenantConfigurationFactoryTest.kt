package com.projectronin.interop.mirth.connector

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.tenant.config.data.MirthTenantConfigDAO
import com.projectronin.interop.tenant.config.data.TenantServerDAO
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class TenantConfigurationFactoryTest {
    lateinit var springContext: AnnotationConfigApplicationContext

    @BeforeEach
    fun setupEnvironment() {
        springContext = mockk()
        mockkObject(TenantConfigurationFactoryImpl)
        every { TenantConfigurationFactoryImpl.context } returns springContext
    }

    @AfterEach
    fun unmockk() {
        unmockkAll()
    }

    @Test
    fun `service factory can be created`() {
        assertNotNull(TenantConfigurationFactoryImpl)
    }

    @Test
    fun `can find values when they exist`() {
        every { springContext.getBean(MirthTenantConfigDAO::class.java) } returns mockk {
            every { getByTenantMnemonic("mdaoc") } returns mockk {
                every { locationIds } returns "12,12312"
            }
        }
        val locations = TenantConfigurationFactoryImpl.getLocationIDsByTenant("mdaoc")
        assertEquals(2, locations.size)
    }

    @Test
    fun `throws error when non-existent`() {
        every { springContext.getBean(MirthTenantConfigDAO::class.java) } returns mockk {
            every { getByTenantMnemonic("fake") } returns null
        }
        assertThrows<IllegalArgumentException> {
            TenantConfigurationFactoryImpl.getLocationIDsByTenant("fake")
        }
    }

    @Test
    fun `can find mdm info`() {
        every { springContext.getBean(TenantServerDAO::class.java) } returns mockk {
            every { getTenantServers("mdaoc", MessageType.MDM) } returns listOf(
                mockk {
                    every { address } returns "ProjectRonin.biz.gov.co.uk.com"
                    every { port } returns 10
                }
            )
        }
        val mdmInfo = TenantConfigurationFactoryImpl.getMDMInfo("mdaoc")
        assertEquals("ProjectRonin.biz.gov.co.uk.com", mdmInfo?.first)
        assertEquals(10, mdmInfo?.second)
    }
}
