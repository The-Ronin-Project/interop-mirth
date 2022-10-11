package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.resource.RoninPractitioner
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
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

class PractitionerQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockVendorFactory: VendorFactory
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: PractitionerQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockVendorFactory = mockk()
        mockServiceFactory = mockk {
            every { vendorFactory(mockTenant) } returns mockVendorFactory
        }
        channel = PractitionerQueue(mockServiceFactory)
    }

    @Test
    fun `create channel - works`() {
        val channel = PractitionerQueue(mockServiceFactory)
        assertEquals(ResourceType.PRACTITIONER, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)
        mockkStatic(Practitioner::transformTo)
        val mockPractitioner = mockk<Practitioner>()
        every { JacksonUtil.readJsonObject<Practitioner>(any(), any()) } returns mockk {
            every { transformTo(RoninPractitioner, any()) } returns mockPractitioner
        }
        val transformed = channel.deserializeAndTransform("practitionerString", mockTenant)
        assertEquals(mockPractitioner, transformed)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        mockkStatic(Practitioner::transformTo)
        every { JacksonUtil.readJsonObject<Practitioner>(any(), any()) } returns mockk {
            every { transformTo(RoninPractitioner, any()) } returns null
        }
        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "practitionerString",
                mockTenant
            )
        }
    }
}
