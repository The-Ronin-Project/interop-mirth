package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
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

class ConditionQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockVendorFactory: VendorFactory
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: ConditionQueue

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
        channel = ConditionQueue(mockServiceFactory)
    }

    @Test
    fun `create channel - works`() {
        val channel = ConditionQueue(mockServiceFactory)
        assertEquals(ResourceType.CONDITION, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {

        mockkObject(JacksonUtil)
        mockkStatic(Condition::transformTo)
        val mockCondition = mockk<Condition>()
        every { JacksonUtil.readJsonObject<Condition>(any(), any()) } returns mockk {
            every { transformTo(RoninConditions, any()) } returns mockCondition
        }
        val transformed = channel.deserializeAndTransform("conditionString", mockTenant)
        assertEquals(mockCondition, transformed)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        mockkStatic(Condition::transformTo)
        every { JacksonUtil.readJsonObject<Condition>(any(), any()) } returns mockk {
            every { transformTo(RoninConditions, any()) } returns null
        }
        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "conditionString",
                mockTenant
            )
        }
    }
}
