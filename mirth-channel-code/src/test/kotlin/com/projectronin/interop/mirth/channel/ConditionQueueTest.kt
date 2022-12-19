package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.mirth.channel.destinations.queue.ConditionQueueWriter
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
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

class ConditionQueueTest {
    private val mockTenant = mockk<Tenant> {
        every { mnemonic } returns "testmnemonic"
    }
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockRoninConditions: RoninConditions
    private lateinit var channel: ConditionQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockTransformManager = mockk()
        mockRoninConditions = mockk()

        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic("testmnemonic") } returns mockTenant
        }
        val conditionQueueWriter = mockk<ConditionQueueWriter>()
        val queueService = mockk<QueueService>()
        channel =
            ConditionQueue(tenantService, mockTransformManager, conditionQueueWriter, queueService, mockRoninConditions)
    }

    @Test
    fun `create channel - works`() {
        assertEquals(ResourceType.CONDITION, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)
        val condition = mockk<Condition>()
        every { JacksonUtil.readJsonObject<Condition>(any(), any()) } returns condition

        val roninCondition = mockk<Condition>()
        every {
            mockTransformManager.transformResource(
                condition,
                mockRoninConditions,
                mockTenant
            )
        } returns roninCondition

        val transformed = channel.deserializeAndTransform("conditionString", mockTenant)
        assertEquals(roninCondition, transformed)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)
        val condition = mockk<Condition>()
        every { JacksonUtil.readJsonObject<Condition>(any(), any()) } returns condition

        every { mockTransformManager.transformResource(condition, mockRoninConditions, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "conditionString",
                mockTenant
            )
        }
    }
}
