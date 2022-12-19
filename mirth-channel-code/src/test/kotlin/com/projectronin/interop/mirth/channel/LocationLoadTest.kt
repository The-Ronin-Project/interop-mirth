package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.mirth.channel.destinations.LocationWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val TENANT_ID = "ronin"

internal class LocationLoadTest {
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns TENANT_ID
    }
    private val vendorFactory = mockk<VendorFactory>()
    private val transformManager = mockk<TransformManager>()
    private var tenantConfigurationFactory = mockk<TenantConfigurationService>()
    private var roninLocation = mockk<RoninLocation>()

    lateinit var channel: LocationLoad

    @BeforeEach
    fun setup() {
        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic(TENANT_ID) } returns tenant
        }
        val ehrFactory = mockk<EHRFactory> {
            every { getVendorFactory(tenant) } returns vendorFactory
        }
        val locationWriter = mockk<LocationWriter>()
        channel = LocationLoad(
            tenantService,
            transformManager,
            locationWriter,
            ehrFactory,
            tenantConfigurationFactory,
            roninLocation
        )
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `sourceReader - no locations for tenant`() {
        every { tenantConfigurationFactory.getLocationIDsByTenant(TENANT_ID) } returns listOf()
        assertThrows<ResourcesNotFoundException> { channel.channelSourceReader(TENANT_ID, mapOf()) }
    }

    @Test
    fun `sourceReader - works`() {
        every { tenantConfigurationFactory.getLocationIDsByTenant(TENANT_ID) } returns listOf(
            "$TENANT_ID-location1", "$TENANT_ID-location2"
        )
        every {
            vendorFactory.locationService.getLocationsByFHIRId(
                tenant,
                listOf("location1", "location2")
            )
        } returns mapOf(
            "location1" to mockk {
                every { resourceType } returns "Location"
            },
            "location2" to mockk()
        )
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "message"

        val response = channel.channelSourceReader(TENANT_ID, mapOf())
        assertEquals("message", response[0].message)
        assertEquals("Location", response[0].dataMap[MirthKey.RESOURCE_TYPE.code])
    }

    @Test
    fun `sourceTransformer - no resources`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("msg", Location::class) } returns listOf()
        assertThrows<ResourcesNotFoundException> {
            channel.channelSourceTransformer(
                TENANT_ID,
                "msg",
                mapOf(),
                mapOf()
            )
        }
    }

    @Test
    fun `sourceTransformer - works`() {
        val location1 = mockk<Location> {
            every { id?.value } returns "location1"
        }
        val location2 = mockk<Location> {
            every { id?.value } returns "location2"
        }
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList("msg", Location::class) } returns listOf(location1, location2)
        every { JacksonUtil.writeJsonValue(any()) } returns "message"

        every { transformManager.transformResource(location1, any(), tenant) } returns location1
        every { transformManager.transformResource(location2, any(), tenant) } returns location2

        val response = channel.channelSourceTransformer(TENANT_ID, "msg", mapOf(), mapOf())
        assertEquals("message", response.message)
        assertEquals("location1,location2", response.dataMap[MirthKey.FHIR_ID_LIST.code])
    }
}
