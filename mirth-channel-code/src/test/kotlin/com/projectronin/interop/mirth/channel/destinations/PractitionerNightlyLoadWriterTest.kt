package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.channel.util.serialize
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val VALID_TENANT_ID = "mdaoc"

class PractitionerNightlyLoadWriterTest {
    lateinit var writer: PractitionerNightlyLoadWriter

    private val publishService = mockk<PublishService>()

    @BeforeEach
    fun setup() {
        val tenantService = mockk<TenantService>()
        val transformManager = mockk<TransformManager>()
        writer = PractitionerNightlyLoadWriter(tenantService, transformManager, publishService)
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
    fun `destinationWriter - empty list of transformed resources returns error message`() {
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to emptyList<Location>())
        val metadata = generateMetadata()
        val response = writer.channelDestinationWriter(
            "ronin",
            "msg",
            mapOf(MirthKey.TENANT_MNEMONIC.code to "ronin", MirthKey.EVENT_METADATA.code to serialize(metadata)),
            channelMap
        )
        assertEquals("No transformed Resource(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
    }

    @Test
    fun `destinationWriter - missing list of transformed resources returns error message`() {
        val metadata = generateMetadata()
        val response = writer.channelDestinationWriter(
            "ronin",
            "msg",
            mapOf(MirthKey.TENANT_MNEMONIC.code to "ronin", MirthKey.EVENT_METADATA.code to serialize(metadata)),
            emptyMap()
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
        val channelMap =
            mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        val metadata = generateMetadata()
        every { publishService.publishFHIRResources(VALID_TENANT_ID, resourceList, metadata) } returns true

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns serializedRoninPractitioner

        val response = writer.destinationWriter(
            "unused",
            "",
            mapOf(
                MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
                MirthKey.EVENT_METADATA.code to serialize(metadata)
            ),
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
            mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        val metadata = generateMetadata()
        every { publishService.publishFHIRResources("ronin", resourceList, metadata) } returns false

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns serializedRoninPractitioner
        val response = writer.channelDestinationWriter(
            "ronin",
            serializedRoninPractitioner,
            mapOf(MirthKey.TENANT_MNEMONIC.code to "ronin", MirthKey.EVENT_METADATA.code to serialize(metadata)),
            channelMap
        )
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("Failed to publish Resource(s)", response.message)
        unmockkObject(JacksonUtil)
    }
}
