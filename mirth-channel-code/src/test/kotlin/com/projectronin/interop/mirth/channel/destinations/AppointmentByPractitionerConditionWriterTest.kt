package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.ConditionService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val VALID_TENANT_ID = "mdaoc"

class AppointmentByPractitionerConditionWriterTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var transformManager: TransformManager
    lateinit var publishService: PublishService
    lateinit var roninConditions: RoninConditions
    lateinit var writer: AppointmentByPractitionerConditionWriter

    private val tenant = mockk<Tenant>() {
        every { mnemonic } returns VALID_TENANT_ID
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        vendorFactory = mockk()
        transformManager = mockk()
        publishService = mockk()
        roninConditions = mockk()

        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic(VALID_TENANT_ID) } returns tenant
        }
        val ehrFactory = mockk<EHRFactory>() {
            every { getVendorFactory(tenant) } returns vendorFactory
        }
        writer = AppointmentByPractitionerConditionWriter(
            tenantService,
            transformManager,
            publishService,
            ehrFactory,
            roninConditions
        )
    }

    @Test
    fun `destinationWriter - works`() {
        val mockCondition = mockk<Condition> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }
        val mockConditions = listOf(mockCondition)
        val mockRoninCondition = mockk<Condition> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }
        val mockRoninConditions = listOf(
            mockRoninCondition
        )
        val mockConditionService = mockk<ConditionService> {
            every {
                findConditionsByCodes(
                    tenant,
                    "blah",
                    any(),
                    any()
                )
            } returns mockConditions
        }
        every { transformManager.transformResource(mockCondition, roninConditions, tenant) } returns mockRoninCondition
        every { vendorFactory.conditionService } returns mockConditionService

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "[]"

        val metadata = mockk<Metadata>()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, mockRoninConditions, metadata) } returns true

        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(
                MirthKey.PATIENT_FHIR_ID.code to "blah",
                MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
                MirthKey.EVENT_METADATA.code to metadata
            ),
            emptyMap()
        )
        assertEquals("Published 1 Condition(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals("[]", response.detailedMessage)
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        val mockCondition = mockk<Condition> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }
        val mockConditions = listOf(mockCondition)

        val mockRoninCondition = mockk<Condition> {
            every { id } returns mockk {
                every { value } returns "blah"
            }
        }
        val mockRoninConditions = listOf(
            mockRoninCondition
        )
        val mockConditionService = mockk<ConditionService> {
            every {
                findConditionsByCodes(
                    tenant,
                    "blah",
                    any(),
                    any()
                )
            } returns mockConditions
        }
        every { transformManager.transformResource(mockCondition, roninConditions, tenant) } returns mockRoninCondition
        every { vendorFactory.conditionService } returns mockConditionService

        val metadata = mockk<Metadata>()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, mockRoninConditions, metadata) } returns false

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(
                MirthKey.PATIENT_FHIR_ID.code to "blah",
                MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
                MirthKey.EVENT_METADATA.code to metadata
            ),
            emptyMap()
        )
        assertEquals("Failed to publish Condition(s)", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("[]", response.detailedMessage)
    }

    @Test
    fun `destinationWriter - no conditions were transformed`() {
        val mockCondition = mockk<Condition> {
            every { id } returns mockk {
                every { value } returns "oh no"
            }
        }
        val mockConditions = listOf(mockCondition)

        val mockConditionService = mockk<ConditionService> {
            every {
                findConditionsByCodes(
                    tenant,
                    "blah",
                    any(),
                    any()
                )
            } returns mockConditions
        }
        every { transformManager.transformResource(mockCondition, roninConditions, tenant) } returns null
        every { vendorFactory.conditionService } returns mockConditionService
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "[]"
        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(
                MirthKey.PATIENT_FHIR_ID.code to "blah",
                MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID
            ),
            emptyMap()
        )
        assertEquals("Failed to transform Conditions for Patient", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals(mockConditions.toString(), response.detailedMessage)
    }

    @Test
    fun `destinationWriter - no conditions found for patient`() {
        val mockConditions = listOf<Condition>()

        val mockConditionService = mockk<ConditionService> {
            every {
                findConditionsByCodes(
                    tenant,
                    "blah",
                    any(),
                    any()
                )
            } returns mockConditions
        }

        every { vendorFactory.conditionService } returns mockConditionService

        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(
                MirthKey.PATIENT_FHIR_ID.code to "blah",
                MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID
            ),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals("No Conditions found for Patient", response.message)
    }

    @Test
    fun `destinationWriter - fails when no patient fhir ID`() {
        val response =
            writer.destinationWriter("unused", "", mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID), emptyMap())
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("No Patient FHIR ID found in channel map", response.message)
        assertEquals("", response.detailedMessage)
    }
}
