package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.outputs.FindPractitionersResponse
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.fhir.r4.valueset.LocationStatus
import com.projectronin.interop.fhir.ronin.transformTo
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.TenantConfigurationFactory
import com.projectronin.interop.mirth.connector.util.asCode
import com.projectronin.interop.tenant.config.exception.ConfigurationMissingException
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
import com.projectronin.interop.ehr.PractitionerService as EHRPractitionerService

private const val VALID_TENANT_ID = "mdaoc"
private const val CHANNEL_ROOT_NAME = "PractitionerLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class PractitionerNightlyLoadTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var serviceFactory: ServiceFactory
    lateinit var tenantConfigurationFactory: TenantConfigurationFactory
    lateinit var channel: PractitionerNightlyLoad

    private val tenant = mockk<Tenant> {
        every { mnemonic } returns "mdaoc"
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @BeforeEach
    fun setup() {
        vendorFactory = mockk()
        tenantConfigurationFactory = mockk()

        serviceFactory = mockk {
            every { getTenant(VALID_TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
            every { tenantConfigurationFactory() } returns tenantConfigurationFactory
        }

        channel = PractitionerNightlyLoad(serviceFactory)
    }

    private val roninIdentifier = listOf(
        Identifier(
            type = CodeableConcept(
                coding = listOf(
                    Coding(
                        system = Uri("http://projectronin.com/id/tenantId"),
                        code = Code(value = "TID"),
                        display = "Ronin-specified Tenant Identifier"
                    )
                ),
                text = "Tenant ID"
            ),
            system = Uri("http://projectronin.com/id/tenantId"),
            value = VALID_TENANT_ID
        )
    )
    private val r4Practitioner = Practitioner(
        id = Id("12345"),
        name = listOf(HumanName(family = "Doe")),
    )

    private val r4Location = Location(
        id = Id("910"),
        status = LocationStatus.ACTIVE.asCode()
    )
    private val r4PractitionerRole = PractitionerRole(
        id = Id("678"),
        specialty = listOf(CodeableConcept(text = "Ronin")),
        practitioner = Reference(reference = "Practitioner/12345")
    )

    @Test
    fun `sourceReader - bad channel name`() {
        val ex = assertThrows<TenantMissingException> {
            channel.sourceReader("unusable", emptyMap())
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceReader - no location IDs in tenant configuration`() {
        val emptyResponse = mockk<FindPractitionersResponse>()
        every { emptyResponse.practitioners } returns listOf()
        every { emptyResponse.locations } returns listOf()
        every { emptyResponse.practitionerRoles } returns listOf()
        val mockPractitionerService = mockk<EHRPractitionerService> {
            every { findPractitionersByLocation(tenant, any()) } returns emptyResponse
        }
        every { vendorFactory.practitionerService } returns mockPractitionerService
        every {
            serviceFactory.tenantConfigurationFactory().getLocationIDsByTenant(VALID_TENANT_ID)
        } returns emptyList()

        val serviceMap = mapOf<String, Any>(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )

        val ex = assertThrows<ConfigurationMissingException> {
            channel.sourceReader(VALID_TENANT_ID, serviceMap)
        }
        assertEquals("No Location IDs configured for tenant $VALID_TENANT_ID", ex.message)
    }

    @Test
    fun `sourceReader - no resource`() {
        val emptyResponse = mockk<FindPractitionersResponse>()
        every { emptyResponse.resources } returns emptyList()

        val mockPractitionerService = mockk<EHRPractitionerService> {
            every { findPractitionersByLocation(tenant, any()) } returns emptyResponse
        }
        every { vendorFactory.practitionerService } returns mockPractitionerService
        every {
            serviceFactory.tenantConfigurationFactory().getLocationIDsByTenant(VALID_TENANT_ID)
        } returns listOf("aaa")

        val serviceMap = mapOf<String, Any>(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )

        val ex = assertThrows<ResourcesNotFoundException> {
            channel.sourceReader(VALID_TENANT_ID, serviceMap)
        }
        assertEquals("No Locations, Practitioners or PractitionerRoles found for tenant $VALID_TENANT_ID", ex.message)
    }

    @Test
    fun `sourceReader - no configuration for tenant`() {
        every {
            serviceFactory.tenantConfigurationFactory().getLocationIDsByTenant("notenant")
        } throws IllegalArgumentException("No Mirth Tenant Configuration object found for notenant")
        val serviceMap = mapOf<String, Any>(
            MirthKey.TENANT_MNEMONIC.code to "notenant",
        )

        val ex = assertThrows<IllegalArgumentException> {
            channel.sourceReader(VALID_TENANT_ID, serviceMap)
        }
        assertEquals("No Mirth Tenant Configuration object found for notenant", ex.message)
    }

    @Test
    fun `sourceReader - works`() {

        val epicPractitionerList = listOf(r4Practitioner)

        val epicLocationsList = listOf(r4Location)

        val epicRolesList = listOf(r4PractitionerRole)

        val resourcesFound = mockk<FindPractitionersResponse> {
            every { resources.isEmpty() } returns false
            every { practitioners } returns epicPractitionerList
            every { locations } returns epicLocationsList
            every { practitionerRoles } returns epicRolesList
        }
        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )
        val expectedList = listOf(
            MirthMessage(
                message = listOf(JacksonManager.objectMapper.writeValueAsString(r4Practitioner)).toString(),
                dataMap = mapOf(
                    "${MirthKey.RESOURCES_FOUND.code}.Practitioner" to listOf(r4Practitioner),
                    MirthKey.RESOURCE_COUNT.code to 1
                )
            ),
            MirthMessage(
                message = listOf(JacksonManager.objectMapper.writeValueAsString(r4Location)).toString(),
                dataMap = mapOf(
                    "${MirthKey.RESOURCES_FOUND.code}.Location" to listOf(r4Location),
                    MirthKey.RESOURCE_COUNT.code to 1
                )
            ),
            MirthMessage(
                message = listOf(JacksonManager.objectMapper.writeValueAsString(r4PractitionerRole)).toString(),
                dataMap = mapOf(
                    "${MirthKey.RESOURCES_FOUND.code}.PractitionerRole" to listOf(
                        r4PractitionerRole
                    ),
                    MirthKey.RESOURCE_COUNT.code to 1
                )
            )
        )

        every { tenantConfigurationFactory.getLocationIDsByTenant(VALID_TENANT_ID) } returns listOf("aaa")

        val mockPractitionerService = mockk<EHRPractitionerService> {
            every { findPractitionersByLocation(tenant, listOf("aaa")) } returns resourcesFound
        }

        every { vendorFactory.practitionerService } returns mockPractitionerService

        val actualList = channel.sourceReader(
            VALID_DEPLOYED_NAME,
            sourceMap
        )
        assertEquals(expectedList.size, actualList.size)
    }

    @Test
    fun `sourceReader - works - a max chunk size is input`() {

        val epicPractitionerList = listOf(r4Practitioner)

        val epicLocationsList = listOf(r4Location)

        val epicRolesList = listOf(r4PractitionerRole)

        val resourcesFound = mockk<FindPractitionersResponse> {
            every { resources.isEmpty() } returns false
            every { practitioners } returns epicPractitionerList
            every { locations } returns epicLocationsList
            every { practitionerRoles } returns epicRolesList
        }
        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
            MirthKey.MAX_CHUNK_SIZE.code to 1
        )
        val expectedList = listOf(
            MirthMessage(
                message = listOf(JacksonManager.objectMapper.writeValueAsString(r4Practitioner)).toString(),
                dataMap = mapOf(
                    "${MirthKey.RESOURCES_FOUND.code}.Practitioner" to listOf(r4Practitioner),
                    MirthKey.RESOURCE_COUNT.code to 1,
                    MirthKey.MAX_CHUNK_SIZE.code to 1
                )
            ),
            MirthMessage(
                message = listOf(JacksonManager.objectMapper.writeValueAsString(r4Location)).toString(),
                dataMap = mapOf(
                    "${MirthKey.RESOURCES_FOUND.code}.Location" to listOf(r4Location),
                    MirthKey.RESOURCE_COUNT.code to 1,
                    MirthKey.MAX_CHUNK_SIZE.code to 1
                )
            ),
            MirthMessage(
                message = listOf(JacksonManager.objectMapper.writeValueAsString(r4PractitionerRole)).toString(),
                dataMap = mapOf(
                    "${MirthKey.RESOURCES_FOUND.code}.PractitionerRole" to listOf(
                        r4PractitionerRole
                    ),
                    MirthKey.RESOURCE_COUNT.code to 1,
                    MirthKey.MAX_CHUNK_SIZE.code to 1
                )
            )
        )

        every { tenantConfigurationFactory.getLocationIDsByTenant(VALID_TENANT_ID) } returns listOf("aaa")

        val mockPractitionerService = mockk<EHRPractitionerService> {
            every { findPractitionersByLocation(tenant, listOf("aaa")) } returns resourcesFound
        }

        every { vendorFactory.practitionerService } returns mockPractitionerService

        val actualList = channel.sourceReader(
            VALID_DEPLOYED_NAME,
            sourceMap
        )
        assertEquals(expectedList.size, actualList.size)
    }

    @Test
    fun `sourceTransformer - RESOURCES_FOUND in sourceMap - practitioner only`() {

        val transformedPractitioner = mockk<Practitioner> {
            every { id } returns Id("$VALID_TENANT_ID-12345")
            every { resourceType } returns "Practitioner"
            every { identifier } returns roninIdentifier
        }
        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
            "${MirthKey.RESOURCES_FOUND.code}.Practitioner" to listOf(r4Practitioner)
        )
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList(any(), Practitioner::class) } returns listOf(mockk())
        every { JacksonUtil.writeJsonValue(any()) } returns "message"
        mockkStatic(Practitioner::transformTo)
        every { any<Practitioner>().transformTo(any(), tenant) } returns transformedPractitioner
        val expectedMessage =
            MirthMessage("", mapOf(MirthKey.RESOURCES_TRANSFORMED.code to listOf(transformedPractitioner)))
        val actualMessage = channel.sourceTransformer(VALID_DEPLOYED_NAME, "", sourceMap, mapOf("b" to "c"))
        assertEquals(
            expectedMessage.dataMap[MirthKey.RESOURCES_TRANSFORMED.code],
            actualMessage.dataMap[MirthKey.RESOURCES_TRANSFORMED.code]
        )
    }

    @Test
    fun `sourceTransformer - RESOURCES_FOUND in sourceMap - practitioner that can't be transformed`() {
        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
            "${MirthKey.RESOURCES_FOUND.code}.Practitioner" to listOf(r4Practitioner)
        )
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList(any(), Practitioner::class) } returns listOf(mockk())
        every { JacksonUtil.writeJsonValue(any()) } returns "message"
        mockkStatic(Practitioner::transformTo)
        every { any<Practitioner>().transformTo(any(), tenant) } returns null

        val ex = assertThrows<ResourcesNotTransformedException> {
            channel.sourceTransformer(VALID_DEPLOYED_NAME, "a", sourceMap, mapOf("b" to "c"))
        }
        assertEquals("Failed to transform resources for tenant $VALID_TENANT_ID", ex.message)
    }

    @Test
    fun `sourceTransformer - RESOURCES_FOUND in sourceMap - location only`() {

        val transformedLocation = mockk<Location> {
            every { id } returns Id("$VALID_TENANT_ID-12345")
            every { resourceType } returns "Location"
            every { identifier } returns roninIdentifier
        }
        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
            "${MirthKey.RESOURCES_FOUND.code}.Location" to listOf(r4Location)
        )
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList(any(), Location::class) } returns listOf(mockk())
        every { JacksonUtil.writeJsonValue(any()) } returns "message"
        mockkStatic(Location::transformTo)
        every { any<Location>().transformTo(any(), tenant) } returns transformedLocation
        val expectedMessage =
            MirthMessage("", mapOf(MirthKey.RESOURCES_TRANSFORMED.code to listOf(transformedLocation)))
        val actualMessage = channel.sourceTransformer(VALID_DEPLOYED_NAME, "", sourceMap, mapOf("b" to "c"))
        assertEquals(
            expectedMessage.dataMap[MirthKey.RESOURCES_TRANSFORMED.code],
            actualMessage.dataMap[MirthKey.RESOURCES_TRANSFORMED.code]
        )
    }

    @Test
    fun `sourceTransformer - RESOURCES_FOUND in sourceMap - practitionerRole only`() {

        val transformedPractitionerRole = mockk<PractitionerRole> {
            every { id } returns Id("$VALID_TENANT_ID-12345")
            every { resourceType } returns "Practitioner"
            every { identifier } returns roninIdentifier
        }
        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
            "${MirthKey.RESOURCES_FOUND.code}.PractitionerRole" to listOf(r4PractitionerRole)
        )
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList(any(), PractitionerRole::class) } returns listOf(mockk())
        every { JacksonUtil.writeJsonValue(any()) } returns "message"
        mockkStatic(PractitionerRole::transformTo)
        every { any<PractitionerRole>().transformTo(any(), tenant) } returns transformedPractitionerRole
        val expectedMessage =
            MirthMessage("", mapOf(MirthKey.RESOURCES_TRANSFORMED.code to listOf(transformedPractitionerRole)))
        val actualMessage = channel.sourceTransformer(VALID_DEPLOYED_NAME, "", sourceMap, mapOf("b" to "c"))
        assertEquals(
            expectedMessage.dataMap[MirthKey.RESOURCES_TRANSFORMED.code],
            actualMessage.dataMap[MirthKey.RESOURCES_TRANSFORMED.code]
        )
    }

    @Test
    fun `sourceTransformer - RESOURCES_FOUND in sourceMap - practitionerRole that can't be transformed`() {

        val sourceMap = mapOf(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
            "${MirthKey.RESOURCES_FOUND.code}.PractitionerRole" to listOf(r4PractitionerRole)
        )
        mockkObject(JacksonUtil)
        every { JacksonUtil.readJsonList(any(), PractitionerRole::class) } returns listOf(mockk())
        every { JacksonUtil.writeJsonValue(any()) } returns "message"
        mockkStatic(PractitionerRole::transformTo)
        every { any<PractitionerRole>().transformTo(any(), tenant) } returns null

        val ex = assertThrows<ResourcesNotTransformedException> {
            channel.sourceTransformer(VALID_DEPLOYED_NAME, "a", sourceMap, mapOf("b" to "c"))
        }
        assertEquals("Failed to transform resources for tenant $VALID_TENANT_ID", ex.message)
    }

    @Test
    fun `sourceTransformer - no RESOURCES_FOUND in sourceMap`() {
        val sourceMap = mapOf(MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID)

        val ex = assertThrows<ResourcesNotFoundException> {
            channel.sourceTransformer(VALID_DEPLOYED_NAME, "a", sourceMap, mapOf("b" to "c"))
        }
        assertEquals("No Locations, Practitioners or PractitionerRoles found for tenant $VALID_TENANT_ID", ex.message)
    }

    @Test
    fun `sourceTransformer - empty RESOURCES_FOUND in sourceMap - nothing to transform`() {
        val resourcesFound = mockk<FindPractitionersResponse> {
            every { practitioners } returns listOf()
            every { locations } returns listOf()
            every { practitionerRoles } returns listOf()
        }
        val sourceMap = mapOf(MirthKey.RESOURCES_FOUND.code to resourcesFound)

        val ex = assertThrows<ResourcesNotFoundException> {
            channel.sourceTransformer(VALID_DEPLOYED_NAME, "b", sourceMap, mapOf("e" to "f"))
        }
        assertEquals("No Locations, Practitioners or PractitionerRoles found for tenant $VALID_TENANT_ID", ex.message)
    }

    @Test
    fun `sourceTransformer - null RESOURCES_FOUND in sourceMap - nothing to transform`() {
        val resourcesFound = mockk<FindPractitionersResponse> {
            every { practitioners } returns listOf()
            every { locations } returns listOf()
            every { practitionerRoles } returns listOf()
        }
        val sourceMap = mapOf(MirthKey.RESOURCES_FOUND.code to resourcesFound)

        val ex = assertThrows<ResourcesNotFoundException> {
            channel.sourceTransformer(VALID_DEPLOYED_NAME, "b", sourceMap, mapOf("e" to "f"))
        }
        assertEquals("No Locations, Practitioners or PractitionerRoles found for tenant $VALID_TENANT_ID", ex.message)
    }
}
