package com.projectronin.interop.mirth.channel.base

import com.fasterxml.jackson.databind.json.JsonMapper
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.resource.ResourceType
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
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BaseQueueTest {
    private val tenantId = "tenant"
    private val mockTenant = mockk<Tenant>()
    private val mockRoninDomainResource = mockk<TestResource>()

    private lateinit var mockTenantService: TenantService
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockQueueWriter: QueueWriter<TestResource>
    private lateinit var mockQueueService: QueueService
    private lateinit var channel: TestQueue

    @BeforeEach
    fun setup() {
        mockQueueService = mockk()

        mockTenantService = mockk<TenantService> {
            every { getTenantForMnemonic(tenantId) } returns mockTenant
        }
        mockTransformManager = mockk<TransformManager>()
        mockQueueWriter = mockk<QueueWriter<TestResource>>()
        channel = TestQueue(mockTenantService, mockTransformManager, mockQueueWriter, mockQueueService)
        mockkObject(JacksonManager)
    }

    @Test
    fun `sourceReader - works`() {
        val queueMessage = "testing!!"
        val mockMetadata = mockk<Metadata>()
        val mockMessage = mockk<ApiMessage> {
            every { text } returns queueMessage
            every { metadata } returns mockMetadata
        }
        every { mockQueueService.dequeueApiMessages(tenantId, ResourceType.BUNDLE, 5) } returns listOf(mockMessage)
        val messages = channel.channelSourceReader(tenantId, emptyMap())
        assertEquals(1, messages.size)
        assertEquals(queueMessage, messages.first().message)
        assertEquals(mockMetadata, messages.first().dataMap[MirthKey.EVENT_METADATA.code])
    }

    @Test
    fun `sourceReader - continues reading from queue if response is full`() {
        val queueMessage = "testing!!"
        val mockMetadata = mockk<Metadata>()
        val mockMessage = mockk<ApiMessage> {
            every { text } returns queueMessage
            every { metadata } returns mockMetadata
        }
        every { mockQueueService.dequeueApiMessages(tenantId, ResourceType.BUNDLE, 5) } returns listOf(
            mockMessage,
            mockMessage,
            mockMessage,
            mockMessage,
            mockMessage
        ) andThen emptyList()

        val messages = channel.channelSourceReader(tenantId, emptyMap())
        assertEquals(5, messages.size)
        assertEquals(queueMessage, messages.first().message)
        assertEquals(mockMetadata, messages.first().dataMap[MirthKey.EVENT_METADATA.code])
    }

    @Test
    fun `sourceTransformer - works`() {
        val fhirID = "I'm a fhir ID!"
        val message = "testing!!"
        val transformedResource = """{
        |  "real": "message"
        |}
        """.trimMargin()
        every { mockRoninDomainResource.id?.value } returns fhirID

        mockkObject(JacksonManager)
        every { JacksonManager.objectMapper.writeValueAsString(mockRoninDomainResource) } returns transformedResource

        val channel = TestQueue(
            mockTenantService,
            mockTransformManager,
            mockQueueWriter,
            mockQueueService,
            mockRoninDomainResource
        )
        val transformedMessage = channel.channelSourceTransformer(tenantId, message, emptyMap(), emptyMap())
        assertEquals(fhirID, transformedMessage.dataMap[MirthKey.FHIR_ID.code])
        assertEquals(transformedResource, transformedMessage.message)
        unmockkConstructor(JsonMapper::class)
        unmockkObject(JacksonManager)
    }

    @Test
    fun `sourceTransformer - no fhir id resource works`() {
        val fhirID = ""
        val message = "testing!!"
        val transformedResource = """{
        |  "real": "message",
        |}
        """.trimMargin()
        every { mockRoninDomainResource.id } returns null
        every { mockRoninDomainResource.resourceType } returns "TestResourceType"

        mockkObject(JacksonManager)
        every { JacksonManager.objectMapper.writeValueAsString(mockRoninDomainResource) } returns transformedResource

        val channel = TestQueue(
            mockTenantService,
            mockTransformManager,
            mockQueueWriter,
            mockQueueService,
            mockRoninDomainResource
        )
        val transformedMessage = channel.channelSourceTransformer(tenantId, message, emptyMap(), emptyMap())
        assertEquals(fhirID, transformedMessage.dataMap[MirthKey.FHIR_ID.code])
        assertEquals(transformedResource, transformedMessage.message)
        unmockkConstructor(JsonMapper::class)
        unmockkObject(JacksonManager)
    }
}

class TestQueue(
    tenantService: TenantService,
    transformManager: TransformManager,
    queueWriter: QueueWriter<TestResource>,
    queueService: QueueService,
    private val mockRoninDomainResource: TestResource = mockk()
) : BaseQueue<TestResource>(tenantService, transformManager, queueWriter, queueService) {
    override val resourceType: ResourceType = ResourceType.BUNDLE
    override val rootName: String = "Test"
    override val limit: Int = 5
    override fun deserializeAndTransform(string: String, tenant: Tenant): TestResource = mockRoninDomainResource
}

class TestResource(
    override val contained: List<ContainedResource> = listOf(),
    override val extension: List<Extension> = listOf(),
    override val modifierExtension: List<Extension> = listOf(),
    override val text: Narrative?,
    override val id: Id?,
    override val implicitRules: Uri?,
    override val language: Code?,
    override var meta: Meta?,
    override val resourceType: String = "TestResourceType"
) : DomainResource<TestResource>
