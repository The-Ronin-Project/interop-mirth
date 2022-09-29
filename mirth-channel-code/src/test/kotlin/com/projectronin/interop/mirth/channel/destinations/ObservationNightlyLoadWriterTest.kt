package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Observation
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
private const val CHANNEL_ROOT_NAME = "ObservationLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class ObservationNightlyLoadTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var serviceFactory: ServiceFactory
    lateinit var writer: ObservationWriter

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

        writer = ObservationWriter(CHANNEL_ROOT_NAME, serviceFactory)
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
        assertEquals("No transformed Observation(s) to publish", response.message)
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
        assertEquals("No transformed Observation(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
    }

    @Test
    fun `destinationWriter - works`() {
        val transformedObservation1 = mockk<Observation> {
            every { id } returns Id("$VALID_TENANT_ID-12345")
            every { resourceType } returns "Observation"
            every { identifier } returns roninIdentifier
        }
        val transformedObservation2 = mockk<Observation> {
            every { id } returns Id("$VALID_TENANT_ID-6789")
            every { resourceType } returns "Observation"
            every { identifier } returns roninIdentifier
        }
        val resourceList = listOf(transformedObservation1, transformedObservation2)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { publishService.publishFHIRResources(VALID_TENANT_ID, resourceList) } returns true

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "[roninObservation, roninObservation]"
        val response = writer.destinationWriter(
            VALID_DEPLOYED_NAME,
            "",
            emptyMap(),
            channelMap
        )
        assertEquals("Published 2 Observation(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals("[roninObservation, roninObservation]", response.detailedMessage)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        val observation = mockk<Observation> {
            every { id } returns Id("12345")
            every { resourceType } returns "Observation"
        }
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "Observation"
        |}
        """.trimMargin()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(observation)
        val channelMap =
            mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList, MirthKey.TENANT_MNEMONIC.code to "ronin")

        every { publishService.publishFHIRResources("ronin", resourceList) } returns false

        val response = writer.channelDestinationWriter(
            "ronin",
            mockSerialized,
            emptyMap(),
            channelMap
        )
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals("Failed to publish Observation(s)", response.message)
        assertEquals(mockSerialized, response.detailedMessage)
        unmockkObject(JacksonUtil)
    }
}
