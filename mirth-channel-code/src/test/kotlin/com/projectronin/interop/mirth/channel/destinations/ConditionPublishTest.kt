package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.ConditionService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConditionPublishTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var transformManager: TransformManager
    lateinit var publishService: PublishService
    lateinit var conditionsService: ConditionService
    lateinit var roninConditions: RoninConditions
    lateinit var writer: ConditionPublish

    private val tenant = mockk<Tenant>() {
        every { mnemonic } returns "mockTenant"
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @BeforeEach
    fun setup() {
        conditionsService = mockk()
        vendorFactory = mockk {
            every { conditionService } returns conditionsService
        }
        transformManager = mockk()
        publishService = mockk()
        roninConditions = mockk()
        mockkObject(JacksonUtil)
        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic("mockTenant") } returns tenant
            every { getTenantForMnemonic("garbo") } returns null
        }
        val ehrFactory = mockk<EHRFactory>() {
            every { getVendorFactory(tenant) } returns vendorFactory
        }
        writer = ConditionPublish(
            tenantService,
            ehrFactory,
            transformManager,
            roninConditions,
            publishService,
        )
    }

    @Test
    fun `writer works`() {
        val mockResource = "mock resource"
        val mockPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "patientId"
            }
        }
        val mockCondition = mockk<Condition> {
            every { id } returns mockk {
                every { value } returns "patientId"
            }
        }

        val transformed = mockk<Condition> {
        }
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.adhoc
            every { resourceJson } returns mockResource
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourcePublishV1::class) } returns mockEvent
        every { JacksonUtil.readJsonObject(mockResource, Patient::class) } returns mockPatient
        every { JacksonUtil.writeJsonValue(listOf(transformed)) } returns "we made it"

        every { conditionsService.findConditionsByCodes(tenant, "patientId", any(), any()) } returns listOf(
            mockCondition
        )
        every { transformManager.transformResource(mockCondition, roninConditions, tenant) } returns transformed
        every { publishService.publishFHIRResources("mockTenant", listOf(transformed), DataTrigger.AD_HOC) } returns true

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 1 conditions(s)", result.message)
    }

    @Test
    fun `writer fails when bad tenant`() {
        assertThrows<IllegalArgumentException> {
            writer.channelDestinationWriter("garbo", "fake event", emptyMap(), emptyMap())
        }
    }

    @Test
    fun `writer fails when can't serialize event`() {
        val exception = assertThrows<IllegalStateException> {
            writer.channelDestinationWriter(
                "mockTenant",
                "fake event",
                mapOf(MirthKey.KAFKA_EVENT.code to "Bad event"),
                emptyMap()
            )
        }
        assertEquals("Received a string which cannot deserialize to a known event", exception.message)
    }

    @Test
    fun `writer fails when didn't pass event name`() {
        val exception = assertThrows<MapVariableMissing> {
            writer.channelDestinationWriter("mockTenant", "fake event", emptyMap(), emptyMap())
        }
        assertEquals("Missing Event Name", exception.message)
    }

    @Test
    fun `writer surfaces publishes errors`() {
        val mockResource = "mock resource"
        val mockPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "patientId"
            }
        }
        val condition1 = mockk<Condition> { every { id?.value } returns "1" }
        val condition2 = mockk<Condition> { every { id?.value } returns "2" }
        val condition3 = mockk<Condition> { every { id?.value } returns "3" }
        val condition4 = mockk<Condition> { every { id?.value } returns "4" }
        val condition5 = mockk<Condition> { every { id?.value } returns "5" }
        val condition6 = mockk<Condition> { every { id?.value } returns "6" }
        val conditionList = listOf(
            condition1,
            condition2,
            condition3,
            condition4,
            condition5,
            condition6,
        )
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { resourceJson } returns mockResource
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourcePublishV1::class) } returns mockEvent
        every { JacksonUtil.readJsonObject(mockResource, Patient::class) } returns mockPatient
        every { JacksonUtil.writeJsonValue(listOf("1", "2", "3", "4", "5", "6")) } returns "properly truncated"

        every { conditionsService.findConditionsByCodes(tenant, "patientId", any(), any()) } returns conditionList
        every { transformManager.transformResource(condition1, roninConditions, tenant) } returns condition1
        every { transformManager.transformResource(condition2, roninConditions, tenant) } returns condition2
        every { transformManager.transformResource(condition3, roninConditions, tenant) } returns condition3
        every { transformManager.transformResource(condition4, roninConditions, tenant) } returns condition4
        every { transformManager.transformResource(condition5, roninConditions, tenant) } returns condition5
        every { transformManager.transformResource(condition6, roninConditions, tenant) } returns condition6

        every { publishService.publishFHIRResources("mockTenant", conditionList, DataTrigger.NIGHTLY) } returns false

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("properly truncated", result.detailedMessage)
        assertEquals("Failed to publish 6 condition(s)", result.message)
    }

    @Test
    fun `writer surfaces ehr calls`() {
        val condition1 = mockk<Condition> { every { id?.value } returns "1" }

        val mockEvent = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.backfill
            every { resourceFHIRId } returns "askedForId"
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourceLoadV1::class) } returns mockEvent
        every { JacksonUtil.writeJsonValue(listOf(condition1)) } returns "raw resource"

        every { conditionsService.getByID(tenant, "askedForId") } throws Exception("EHR is toast")
        every { transformManager.transformResource(condition1, roninConditions, tenant) } returns null

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("EHR is toast", result.detailedMessage)
        assertEquals("Failed EHR Call", result.message)
    }

    @Test
    fun `writer surfaces transform errors`() {
        val condition1 = mockk<Condition> { every { id?.value } returns "1" }

        val mockEvent = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.backfill
            every { resourceFHIRId } returns "askedForId"
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourceLoadV1::class) } returns mockEvent
        every { JacksonUtil.writeJsonValue(listOf(condition1)) } returns "raw resource"

        every { conditionsService.getByID(tenant, "askedForId") } returns condition1
        every { transformManager.transformResource(condition1, roninConditions, tenant) } returns null

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("raw resource", result.detailedMessage)
        assertEquals("Failed to transform 1 condition(s)", result.message)
    }

    @Test
    fun `writer surfaces transform errors - codecov`() {
        val mockResource = "mock resource"
        val mockPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "patientId"
            }
        }
        val condition1 = mockk<Condition> { every { id?.value } returns "1" }
        val condition2 = mockk<Condition> { every { id?.value } returns "2" }
        val condition3 = mockk<Condition> { every { id?.value } returns "3" }
        val condition4 = mockk<Condition> { every { id?.value } returns "4" }
        val condition5 = mockk<Condition> { every { id?.value } returns "5" }
        val condition6 = mockk<Condition> { every { id?.value } returns null }
        val conditionList = listOf(
            condition1,
            condition2,
            condition3,
            condition4,
            condition5,
            condition6,
        )
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.adhoc
            every { resourceJson } returns mockResource
        }

        every { JacksonUtil.readJsonObject(any(), InteropResourcePublishV1::class) } returns mockEvent
        every { JacksonUtil.writeJsonValue(listOf("1", "2", "3", "4", "5", null)) } returns "properly truncated raw"
        every { JacksonUtil.readJsonObject(mockResource, Patient::class) } returns mockPatient

        every { conditionsService.findConditionsByCodes(tenant, "patientId", any(), any()) } returns conditionList
        every { transformManager.transformResource(any(), roninConditions, tenant) } returns null

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("properly truncated raw", result.detailedMessage)
        assertEquals("Failed to transform 6 condition(s)", result.message)
    }

    @Test
    fun `writer doesn't support backfill yet`() {
        val mockResource = "mock resource"
        val mockPatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "patientId"
            }
        }
        val mockEvent = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.backfill
            every { resourceJson } returns mockResource
        }

        every { JacksonUtil.readJsonObject(any(), InteropResourcePublishV1::class) } returns mockEvent

        val exception = assertThrows<IllegalStateException> {
            writer.channelDestinationWriter(
                "mockTenant",
                "fake event",
                mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
                emptyMap()
            )
        }
        assertEquals("Received a data trigger which cannot be transformed to a known value", exception.message)
    }
}
