package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.TenantConfigurationFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
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
    private var tenantConfigurationFactory = mockk<TenantConfigurationFactory>()
    lateinit var serviceFactory: ServiceFactory
    lateinit var channel: LocationLoad

    @BeforeEach
    fun setup() {
        serviceFactory = mockk {
            every { getTenant(TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
            every { tenantConfigurationFactory() } returns tenantConfigurationFactory
        }
        channel = LocationLoad(serviceFactory)
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
        mockkStatic(Location::transformTo)
        every { location1.transformTo(any(), tenant) } returns location1
        every { location2.transformTo(any(), tenant) } returns location2
        val response = channel.channelSourceTransformer(TENANT_ID, "msg", mapOf(), mapOf())
        assertEquals("message", response.message)
        assertEquals("location1,location2", response.dataMap[MirthKey.FHIR_ID_LIST.code])
    }
}
