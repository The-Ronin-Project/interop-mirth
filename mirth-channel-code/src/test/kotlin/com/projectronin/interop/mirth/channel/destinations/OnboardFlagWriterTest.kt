package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.TenantService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OnboardFlagWriterTest {

    private lateinit var channel: OnboardFlagWriter
    private val ehrFactory: EHRFactory = mockk()
    private val tenantService: TenantService = mockk()
    private val tenantConfigService: TenantConfigurationService = mockk()

    @BeforeEach
    fun setup() {
        channel = OnboardFlagWriter(ehrFactory, tenantService, tenantConfigService)
    }

    @Test
    fun `destination writer works`() {
        every { tenantService.getTenantForMnemonic(any()) } returns mockk()
        every { ehrFactory.getVendorFactory(any()) } returns mockk {
            every { onboardFlagService } returns mockk {
                every { vendorType } returns VendorType.EPIC
                coEvery { setOnboardedFlag(any(), any()) } returns true
            }
        }
        val result =
            channel.channelDestinationWriter("tenant", "12345", mapOf(MirthKey.FHIR_ID.code to "12345"), emptyMap())
        assertEquals(result.status, MirthResponseStatus.SENT)
    }

    @Test
    fun `destination writer errors`() {
        every { tenantService.getTenantForMnemonic(any()) } returns mockk()
        every { ehrFactory.getVendorFactory(any()) } returns mockk {
            every { vendorType } returns VendorType.EPIC
            every { onboardFlagService } returns mockk {
                coEvery { setOnboardedFlag(any(), any()) } throws Exception("bad!")
            }
        }
        val result =
            channel.channelDestinationWriter("tenant", "12345", mapOf(MirthKey.FHIR_ID.code to "12345"), emptyMap())
        assertEquals(result.status, MirthResponseStatus.ERROR)
    }

    @Test
    fun `destination filter works`() {
        every { tenantConfigService.getConfiguration(any()) } returns mockk {
            every { blockedResources } returns "PatientOnboardFlag,MedicationStatement"
        }
        val result =
            channel.channelDestinationFilter("tenant", "12345", mapOf(MirthKey.FHIR_ID.code to "12345"), emptyMap())
        assertFalse(result.result)
    }

    @Test
    fun `destination filter works - no blocked resources`() {
        every { tenantConfigService.getConfiguration(any()) } returns mockk {
            every { blockedResources } returns null
        }
        val result =
            channel.channelDestinationFilter("tenant", "12345", mapOf(MirthKey.FHIR_ID.code to "12345"), emptyMap())
        assertTrue(result.result)
    }

    @Test
    fun `bad tenant`() {
        every { tenantService.getTenantForMnemonic(any()) } returns null
        assertThrows<IllegalArgumentException> {
            channel.channelDestinationWriter(
                "tenant",
                "12345",
                emptyMap(),
                emptyMap()
            )
        }
    }
}
