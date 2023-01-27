package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PatientPublishTest {
    lateinit var tenant: Tenant
    lateinit var publishService: PublishService
    lateinit var kafkaPushResponse: PushResponse<String>
    lateinit var roninPatient: RoninPatient
    lateinit var transformManager: TransformManager
    lateinit var tenantService: TenantService
    lateinit var channel: PatientPublish

    @BeforeEach
    fun setup() {
        publishService = mockk()
        tenantService = mockk()
        tenant = mockk {
            every { mnemonic } returns "ronin"
        }
        every { tenantService.getTenantForMnemonic("ronin") } returns tenant
        transformManager = mockk()
        roninPatient = mockk()
        kafkaPushResponse = mockk() {
            every { successful } returns listOf("yes")
            every { failures } returns emptyList()
        }
        channel = PatientPublish(tenantService, publishService, transformManager, roninPatient)
    }

    @Test
    fun `channelDestinationWriter  - works`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockk()
        every {
            publishService.publishFHIRResources(
                "ronin", any(), any()
            )
        } returns true

        val result = channel.channelDestinationWriter(
            "ronin",
            "[\"123\",\"456\"]",
            mapOf(MirthKey.DATA_TRIGGER.code to DataTrigger.NIGHTLY),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `channelDestinationWriter  - catches error`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockk()
        every {
            publishService.publishFHIRResources(
                "ronin", any(), any()
            )
        } returns false

        val result = channel.channelDestinationWriter(
            "ronin",
            "[\"123\",\"456\"]",
            mapOf(MirthKey.DATA_TRIGGER.code to DataTrigger.NIGHTLY),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `channelDestinationTransformer works`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockk()
        every { JacksonUtil.writeJsonValue(any()) } returns "json"
        every {
            transformManager.transformResource(
                any(),
                roninPatient,
                tenant
            )
        } returns mockk { every { id!!.value!! } returns "123" }
        val ret = channel.channelDestinationTransformer(
            "ronin", "json", mapOf(MirthKey.DATA_TRIGGER.code to DataTrigger.NIGHTLY),
            emptyMap()
        )
        assertEquals(ret.dataMap[MirthKey.TENANT_MNEMONIC.code], "ronin")
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `channelDestinationTransformer faliures`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonObject(any(), Patient::class) } returns mockk()
        every { transformManager.transformResource(any(), roninPatient, any()) } returns null
        assertThrows<ResourcesNotTransformedException> {
            channel.channelDestinationTransformer(
                "ronin", "json", emptyMap(), emptyMap()
            )
        }
        every { tenantService.getTenantForMnemonic("ronin") } returns null
        assertThrows<Exception> {
            channel.channelDestinationTransformer(
                "ronin", "json", emptyMap(), emptyMap()
            )
        }
        unmockkObject(JacksonUtil)
    }
}
