package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.mirth.channel.destinations.queue.ConditionTenantlessQueueWriter
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.rcdm.transform.TransformManager
import com.projectronin.interop.rcdm.transform.model.TransformResponse
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

class KafkaConditionQueueTest {
    private val mockTenant =
        mockk<Tenant> {
            every { mnemonic } returns "testmnemonic"
        }
    private lateinit var mockTransformManager: TransformManager
    private lateinit var channel: KafkaConditionQueue

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        mockTransformManager = mockk()
        val tenantService =
            mockk<TenantService> {
                every { getTenantForMnemonic("testmnemonic") } returns mockTenant
            }
        val queueService = mockk<KafkaQueueService>()
        val queueWriter = mockk<ConditionTenantlessQueueWriter>()

        channel =
            KafkaConditionQueue(tenantService, queueService, queueWriter, mockTransformManager)
    }

    @Test
    fun `create channel - works`() {
        assertEquals(ResourceType.CONDITION, channel.resourceType)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `deserializeAndTransform - works`() {
        mockkObject(JacksonUtil)

        val mockCondition = mockk<Condition>()
        every { JacksonUtil.readJsonObject<Condition>(any(), any()) } returns mockCondition

        val roninCondition = mockk<Condition>()
        val transformResponse = TransformResponse(roninCondition)
        every {
            mockTransformManager.transformResource(
                mockCondition,
                mockTenant,
            )
        } returns transformResponse

        val transformedCondition = channel.deserializeAndTransform("conditionString", mockTenant)
        assertEquals(transformResponse, transformedCondition)
    }

    @Test
    fun `deserializeAndTransform - fails`() {
        mockkObject(JacksonUtil)

        val mockCondition = mockk<Condition>()
        every { JacksonUtil.readJsonObject<Condition>(any(), any()) } returns mockCondition

        every { mockTransformManager.transformResource(mockCondition, mockTenant) } returns null

        assertThrows<ResourcesNotTransformedException> {
            channel.deserializeAndTransform(
                "conditionString",
                mockTenant,
            )
        }
    }
}
