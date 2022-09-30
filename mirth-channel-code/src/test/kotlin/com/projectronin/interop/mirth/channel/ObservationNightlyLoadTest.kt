package com.projectronin.interop.mirth.channel

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.ObservationService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.valueset.ObservationStatus
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.util.asCode
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.exception.TenantMissingException
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

private const val VALID_TENANT_ID = "mdaoc"
private const val CHANNEL_ROOT_NAME = "ObservationLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class ObservationNightlyLoadTest {
    lateinit var tenant: Tenant
    lateinit var vendorFactory: VendorFactory
    lateinit var serviceFactory: ServiceFactory
    lateinit var channel: ObservationNightlyLoad

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "mdaoc"
        }

        vendorFactory = mockk()

        serviceFactory = mockk {
            every { getTenant(VALID_TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
        }

        channel = ObservationNightlyLoad(serviceFactory)
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    val expectedObservationJson1 = """
            |{
            |  "resourceType" : "Observation",
            |  "subject" : {
            |    "reference" : "Patient/123"
            |  },
            |  "code" : {
            |    "text" : "code1"
            |  }
            |}""".trimMargin()
    val expectedObservationJson2 = """
            |{
            |  "resourceType" : "Observation",
            |  "subject" : {
            |    "reference" : "Patient/456"
            |  },
            |  "code" : {
            |    "text" : "code2"
            |  }
            |}""".trimMargin()
    val expectedObservationJson3 = """
            |{
            |  "resourceType" : "Observation",
            |  "subject" : {
            |    "reference" : "Patient/123"
            |  },
            |  "code" : {
            |    "text" : "code3"
            |  }
            |}""".trimMargin()
    val expectedObservationJson4 = """
            |{
            |  "resourceType" : "Observation",
            |  "subject" : {
            |    "reference" : "Patient/456"
            |  },
            |  "code" : {
            |    "text" : "code4"
            |  }
            |}""".trimMargin()
    val expectedObservationJson5 = """
            |{
            |  "resourceType" : "Observation",
            |  "subject" : {
            |    "reference" : "Patient/123"
            |  },
            |  "code" : {
            |    "text" : "code5"
            |  }
            |}""".trimMargin()
    private val r4Observation1 = Observation(
        id = Id("12345"),
        status = ObservationStatus.FINAL.asCode(),
        category = listOf(CodeableConcept(text = "category")),
        code = CodeableConcept(text = "code1"),
        subject = Reference(reference = "Patient/123"),
    )
    val r4Observation2 = Observation(
        id = Id("23456"),
        status = ObservationStatus.FINAL.asCode(),
        category = listOf(CodeableConcept(text = "category2")),
        code = CodeableConcept(text = "code2"),
        subject = Reference(reference = "Patient/456"),
    )
    val r4Observation3 = Observation(
        id = Id("34567"),
        status = ObservationStatus.FINAL.asCode(),
        category = listOf(CodeableConcept(text = "category3")),
        code = CodeableConcept(text = "code3"),
        subject = Reference(reference = "Patient/123"),
    )
    val r4Observation4 = Observation(
        id = Id("45678"),
        status = ObservationStatus.FINAL.asCode(),
        category = listOf(CodeableConcept(text = "category4")),
        code = CodeableConcept(text = "code4"),
        subject = Reference(reference = "Patient/456"),
    )
    val r4Observation5 = Observation(
        id = Id("56789"),
        status = ObservationStatus.FINAL.asCode(),
        category = listOf(CodeableConcept(text = "category5")),
        code = CodeableConcept(text = "code5"),
        subject = Reference(reference = "Patient/456"),
    )

    @Test
    fun `sourceReader - bad channel name`() {
        val ex = assertThrows<TenantMissingException> {
            channel.sourceReader("unusable", emptyMap())
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceReader - no patients for tenant`() {
        val serviceMap = mapOf(MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID)

        val mockPatientService: PatientService = mockk {
            every { getPatientFHIRIdsByTenant(VALID_TENANT_ID) } returns emptyList()
        }
        every { serviceFactory.patientService() } returns mockPatientService

        val ex = assertThrows<ResourcesNotFoundException> {
            channel.sourceReader(VALID_DEPLOYED_NAME, serviceMap)
        }
        assertEquals(
            "No Patients found in clinical data store for tenant $VALID_TENANT_ID",
            ex.message
        )
    }

    @Test
    fun `sourceReader - resources found for tenant`() {
        val resourcesFound = listOf(r4Observation1)

        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )
        val expectedList = listOf(
            MirthMessage(
                message = listOf(expectedObservationJson1).toString(),
                dataMap = mapOf(
                    MirthKey.PATIENT_FHIR_ID.code to "123",
                    MirthKey.RESOURCES_FOUND.code to listOf(r4Observation1),
                    MirthKey.RESOURCE_TYPE.code to ResourceType.OBSERVATION.name,
                    MirthKey.RESOURCE_COUNT.code to 1
                )
            ),
            MirthMessage(
                message = listOf(expectedObservationJson1).toString(),
                dataMap = mapOf(
                    MirthKey.PATIENT_FHIR_ID.code to "456",
                    MirthKey.RESOURCES_FOUND.code to listOf(r4Observation1),
                    MirthKey.RESOURCE_TYPE.code to ResourceType.OBSERVATION.name,
                    MirthKey.RESOURCE_COUNT.code to 1
                )
            ),
        )

        val mockObservationService = mockk<ObservationService> {
            every { findObservationsByPatientAndCategory(tenant, any(), any()) } returns resourcesFound
        }
        every { vendorFactory.observationService } returns mockObservationService

        val mockPatientService = mockk<PatientService> {
            every { getPatientFHIRIdsByTenant("mdaoc") } returns listOf(
                "mdaoc-123",
                "mdaoc-456"
            )
        }
        every { serviceFactory.patientService() } returns mockPatientService

        val actualList = channel.sourceReader(
            VALID_DEPLOYED_NAME,
            sourceMap
        )
        assertEquals(expectedList.size, actualList.size)
    }

    @Test
    fun `sourceReader - resources found for tenant - multiple patients and observations`() {

        val resourcesFound123 = listOf(
            r4Observation1,
            r4Observation3,
            r4Observation5
        )

        val resourcesFound456 = listOf(
            r4Observation2,
            r4Observation4,
        )

        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )
        val expectedList = listOf(
            MirthMessage(
                message = listOf(
                    expectedObservationJson1,
                    expectedObservationJson3,
                    expectedObservationJson5
                ).toString(),
                dataMap = mapOf(
                    MirthKey.PATIENT_FHIR_ID.code to "123",
                    MirthKey.RESOURCES_FOUND.code to listOf(
                        r4Observation1,
                        r4Observation3,
                        r4Observation5
                    ),
                    MirthKey.RESOURCE_TYPE.code to ResourceType.OBSERVATION.name,
                    MirthKey.RESOURCE_COUNT.code to 3
                )
            ),
            MirthMessage(
                message = listOf(
                    expectedObservationJson2,
                    expectedObservationJson4
                ).toString(),
                dataMap = mapOf(
                    MirthKey.PATIENT_FHIR_ID.code to "456",
                    MirthKey.RESOURCES_FOUND.code to listOf(
                        r4Observation2,
                        r4Observation4
                    ),
                    MirthKey.RESOURCE_TYPE.code to ResourceType.OBSERVATION.name,
                    MirthKey.RESOURCE_COUNT.code to 2
                )
            ),
        )

        val mockObservationService = mockk<ObservationService> {
            every { findObservationsByPatientAndCategory(tenant, listOf("123"), any()) } returns resourcesFound123
            every { findObservationsByPatientAndCategory(tenant, listOf("456"), any()) } returns resourcesFound456
        }
        every { vendorFactory.observationService } returns mockObservationService

        val mockPatientService = mockk<PatientService> {
            every { getPatientFHIRIdsByTenant("mdaoc") } returns listOf(
                "mdaoc-123",
                "mdaoc-456"
            )
        }
        every { serviceFactory.patientService() } returns mockPatientService

        val actualList = channel.sourceReader(
            VALID_DEPLOYED_NAME,
            sourceMap
        )
        assertEquals(expectedList.size, actualList.size)
    }

    @Test
    fun `sourceTransformer - no resources found`() {
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList<Observation>(any(), any()) } returns listOf()
        assertThrows<ResourcesNotFoundException> {
            channel.sourceTransformer(
                VALID_DEPLOYED_NAME,
                "a",
                emptyMap(),
                mapOf("b" to "c")
            )
        }
    }

    @Test
    fun `sourceTransformer - works`() {
        mockkObject(JacksonUtil)
        mockkStatic(Observation::transformTo)
        every { JacksonUtil.readJsonList<Observation>(any(), any()) } returns listOf(mockk())
        every { any<Observation>().transformTo(any(), tenant) } returns mockk {
            every { id?.value } returns "id"
        }
        every { JacksonUtil.writeJsonValue(any()) } returns "message"
        val actualMessage =
            channel.sourceTransformer(VALID_DEPLOYED_NAME, "a", emptyMap(), mapOf("b" to "c"))
        assertEquals("message", actualMessage.message)
    }

    @Test
    fun `sourceTransformer - observations can't be transformed`() {
        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
            MirthKey.RESOURCES_FOUND.code to listOf(r4Observation1, r4Observation2, mockk())
        )
        mockkObject(JacksonUtil)
        mockkStatic(Observation::transformTo)
        every { JacksonUtil.readJsonList<Observation>(any(), any()) } returns listOf(mockk())
        every { JacksonUtil.writeJsonValue(any()) } returns "message"
        every { any<Observation>().transformTo(any(), tenant) } returns null

        val ex = assertThrows<ResourcesNotTransformedException> {
            channel.sourceTransformer(VALID_DEPLOYED_NAME, "a", sourceMap, mapOf("b" to "c"))
        }
        assertEquals("Failed to transform Observations for tenant $VALID_TENANT_ID", ex.message)
    }
}