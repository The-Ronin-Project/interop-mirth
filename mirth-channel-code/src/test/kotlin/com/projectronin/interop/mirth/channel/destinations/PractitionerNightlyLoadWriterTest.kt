package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.exception.TenantMissingException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val VALID_TENANT_ID = "mdaoc"
private const val CHANNEL_ROOT_NAME = "PractitionerLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class PractitionerNightlyLoadTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var serviceFactory: ServiceFactory
    lateinit var writer: PractitionerNightlyLoadWriter

    private val tenant = mockk<Tenant>()
    private val publishService = mockk<PublishService>()

    @BeforeEach
    fun setup() {
        vendorFactory = mockk()

        serviceFactory = mockk {
            every { getTenant(VALID_TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
            every { publishService() } returns publishService
        }

        writer = PractitionerNightlyLoadWriter(CHANNEL_ROOT_NAME, serviceFactory)
    }

    private val roninIdentifier = listOf(
        Identifier(
            type = CodeableConcept(
                coding = listOf(
                    Coding(
                        system = Uri("http://projectronin.com/id/tenantId"),
                        code = Code(value = "TID"),
                        display = "Ronin-specified Tenant Identifier".asFHIR()
                    )
                ),
                text = "Tenant ID".asFHIR()
            ),
            system = Uri("http://projectronin.com/id/tenantId"),
            value = VALID_TENANT_ID.asFHIR()
        )
    )

    @Test
    fun `destinationWriter - bad channel name`() {
        val serviceMap = mapOf("b" to "c")
        val ex = assertThrows<TenantMissingException> {
            writer.destinationWriter(
                "unusable",
                "a",
                serviceMap,
                serviceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `destinationWriter - empty list of transformed resources returns error message`() {
        val channelMap = mapOf(
            MirthKey.RESOURCES_TRANSFORMED.code to emptyList<Location>(),
            MirthKey.TENANT_MNEMONIC.code to "ronin"
        )
        val response = writer.channelDestinationWriter(
            "ronin",
            "msg",
            mapOf(),
            channelMap
        )
        assertEquals("No transformed Resource(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
    }

    @Test
    fun `destinationWriter - missing list of transformed resources returns error message`() {
        val channelMap = mapOf(MirthKey.TENANT_MNEMONIC.code to "ronin")
        val response = writer.channelDestinationWriter(
            "ronin",
            "msg",
            mapOf(),
            channelMap
        )
        assertEquals("No transformed Resource(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
    }

    @Test
    fun `destinationWriter - works`() {
        val serializedRoninPractitioner = "roninPractitioner"
        val transformedPractitioner = mockk<Practitioner> {
            every { id } returns Id("$VALID_TENANT_ID-12345")
            every { resourceType } returns "Practitioner"
            every { identifier } returns roninIdentifier
        }
        val resourceList = listOf(transformedPractitioner)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { publishService.publishFHIRResources(VALID_TENANT_ID, resourceList) } returns true

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns serializedRoninPractitioner

        val response = writer.destinationWriter(
            VALID_DEPLOYED_NAME,
            "",
            emptyMap(),
            channelMap
        )
        assertEquals("Published 1 Resource(s)", response.message)
        assertEquals(serializedRoninPractitioner, response.detailedMessage)
        assertEquals(MirthResponseStatus.SENT, response.status)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        val serializedRoninPractitioner = "roninPractitioner"
        val transformedPractitioner = mockk<Practitioner> {
            every { id } returns Id("$VALID_TENANT_ID-12345")
            every { resourceType } returns "Practitioner"
            every { identifier } returns roninIdentifier
        }
        val resourceList = listOf(transformedPractitioner)
        val channelMap =
            mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList, MirthKey.TENANT_MNEMONIC.code to "ronin")

        every { publishService.publishFHIRResources("ronin", resourceList) } returns false

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns serializedRoninPractitioner
        val response = writer.channelDestinationWriter(
            "ronin",
            serializedRoninPractitioner,
            emptyMap(),
            channelMap
        )
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("Failed to publish Resource(s)", response.message)
        unmockkObject(JacksonUtil)
    }
}
