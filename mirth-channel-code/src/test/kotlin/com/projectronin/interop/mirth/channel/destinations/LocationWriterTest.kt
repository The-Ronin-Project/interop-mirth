package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.publishers.PublishService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationWriterTest {
    private val publishService = mockk<PublishService>()
    lateinit var serviceFactory: ServiceFactory
    lateinit var writer: LocationWriter

    @BeforeEach
    fun setup() {
        serviceFactory = mockk {
            every { publishService() } returns publishService
        }
        writer = LocationWriter("LocationLoad", serviceFactory)
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        val location = mockk<Location> {
            every { id } returns Id("12345")
            every { resourceType } returns "Location"
        }
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "Location"
        |}
        """.trimMargin()

        val resourceList = listOf(location)
        val channelMap =
            mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList, MirthKey.TENANT_MNEMONIC.code to "ronin")

        every { publishService.publishFHIRResources("ronin", resourceList) } returns false

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized
        val response = writer.channelDestinationWriter(
            "ronin",
            mockSerialized,
            emptyMap(),
            channelMap
        )
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
        assertEquals("Failed to publish Location(s)", response.message)
    }

    @Test
    fun `destinationWriter - works`() {
        val location = mockk<Location> {
            every { id } returns Id("12345")
            every { resourceType } returns "Location"
        }
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "Location"
        |}
        """.trimMargin()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(location)
        val channelMap =
            mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList, MirthKey.TENANT_MNEMONIC.code to "ronin")

        every { publishService.publishFHIRResources("ronin", any<List<Location>>()) } returns true

        val response = writer.destinationWriter(
            "ronin-LocationLoad",
            "",
            emptyMap(),
            channelMap
        )
        assertEquals("Published 1 Location(s)", response.message)
        assertEquals(mockSerialized, response.detailedMessage)
        assertEquals(MirthResponseStatus.SENT, response.status)
    }

    @Test
    fun `destination writer - empty list of transformed resources returns error message`() {
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
        assertEquals("No transformed Location(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
    }

    @Test
    fun `destination writer - missing list of transformed resources returns error message`() {
        val channelMap = mapOf(MirthKey.TENANT_MNEMONIC.code to "ronin")
        val response = writer.channelDestinationWriter(
            "ronin",
            "msg",
            mapOf(),
            channelMap
        )
        assertEquals("No transformed Location(s) to publish", response.message)
        assertEquals(MirthResponseStatus.ERROR, response.status)
    }
}
