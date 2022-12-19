package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.datatype.Extension
import com.projectronin.interop.fhir.r4.datatype.Meta
import com.projectronin.interop.fhir.r4.datatype.Narrative
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.ContainedResource
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.destinations.queue.QueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QueueWriterTest {
    private val tenantId = "tenant"

    private lateinit var mockPublishService: PublishService
    private lateinit var writer: QueueWriter<TestResource>

    @BeforeEach
    fun setup() {
        mockPublishService = mockk()

        val tenantService = mockk<TenantService>()
        val transformManager = mockk<TransformManager>()
        writer = object :
            QueueWriter<TestResource>(tenantService, transformManager, mockPublishService, TestResource::class) {}
    }

    @AfterEach
    fun unMock() {
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `destinationWriter - works`() {
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "TestResource"
        |}
        """.trimMargin()
        val mockRoninDomainResource = mockk<TestResource>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns mockSerialized

        val resourceList = listOf(mockRoninDomainResource)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { mockPublishService.publishFHIRResources(tenantId, any<List<TestResource>>()) } returns true

        val response = writer.channelDestinationWriter(
            tenantId,
            mockSerialized,
            emptyMap(),
            channelMap
        )
        assertEquals("Published 1 TestResource(s)", response.message)
        assertEquals(MirthResponseStatus.SENT, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
    }

    @Test
    fun `destinationWriter - has resource but publish fails`() {
        val mockSerialized = """{
        |  "id": "12345",
        |  "resourceType": "TestResource"
        |}
        """.trimMargin()
        val mockRoninDomainResource = mockk<TestResource>()

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any<TestResource>()) } returns mockSerialized

        val resourceList = listOf(mockRoninDomainResource)
        val channelMap = mapOf(MirthKey.RESOURCES_TRANSFORMED.code to resourceList)

        every { mockPublishService.publishFHIRResources(tenantId, any<List<TestResource>>()) } returns false

        val response = writer.channelDestinationWriter(
            tenantId,
            mockSerialized,
            emptyMap(),
            channelMap
        )
        assertEquals(MirthResponseStatus.ERROR, response.status)
        assertEquals(mockSerialized, response.detailedMessage)
        assertEquals("Failed to publish TestResource(s)", response.message)
    }
}

class TestResource(
    override val contained: List<ContainedResource> = listOf(),
    override val extension: List<Extension> = listOf(),
    override val modifierExtension: List<Extension> = listOf(),
    override val text: Narrative?,
    override val id: Id?,
    override val implicitRules: Uri?,
    override val language: Code?,
    override val meta: Meta?,
    override val resourceType: String = "TestResourceType"
) : DomainResource<TestResource>
