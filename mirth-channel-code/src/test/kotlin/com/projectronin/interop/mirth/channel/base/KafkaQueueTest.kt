package com.projectronin.interop.mirth.channel.base

import com.fasterxml.jackson.databind.json.JsonMapper
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.destinations.queue.TenantlessQueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.queue.kafka.KafkaQueueService
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

class KafkaQueueTest {
    private val tenantId = "tenant"
    private val mockTenant = mockk<Tenant>()
    private val mockRoninDomainResource = mockk<TestResource>()

    private lateinit var mockTenantService: TenantService
    private lateinit var mockTransformManager: TransformManager
    private lateinit var mockQueueService: KafkaQueueService
    private lateinit var mockQueueWriter: TenantlessQueueWriter<TestResource>
    private lateinit var channel: KafkaTestQueue

    @BeforeEach
    fun setup() {
        mockQueueService = mockk()
        mockTransformManager = mockk()
        mockTenantService = mockk {
            every { getTenantForMnemonic(tenantId) } returns mockTenant
        }
        mockQueueWriter = mockk()
        channel = KafkaTestQueue(mockTenantService, mockTransformManager, mockQueueService, mockQueueWriter)
        mockkObject(JacksonManager)
    }

    @Test
    fun `sourceReader - works`() {
        val queueMessage = "testing!!"
        val mockMessage = mockk<ApiMessage> {
            every { text } returns queueMessage
            every { tenant } returns tenantId
        }
        every { mockQueueService.dequeueApiMessages("", ResourceType.BUNDLE, 5) } returns listOf(mockMessage)

        val messages = channel.sourceReader("name", mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId))
        assertEquals(1, messages.size)
        assertEquals(queueMessage, messages.first().message)
    }

    @Test
    fun `sourceReader - continues reading from queue if response is full`() {
        val queueMessage = "testing!!"
        val mockMessage = mockk<ApiMessage> {
            every { tenant } returns tenantId
            every { text } returns queueMessage
        }
        every { mockQueueService.dequeueApiMessages("", ResourceType.BUNDLE, 5) } returns listOf(
            mockMessage,
            mockMessage,
            mockMessage,
            mockMessage,
            mockMessage
        ) andThen emptyList()

        val messages = channel.sourceReader("name", mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId))
        assertEquals(5, messages.size)
        assertEquals(queueMessage, messages.first().message)
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

        val channel = KafkaTestQueue(mockTenantService, mockTransformManager, mockQueueService, mockQueueWriter, mockRoninDomainResource)
        val transformedMessage =
            channel.sourceTransformer("name", message, mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId), emptyMap())
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

        val channel = KafkaTestQueue(mockTenantService, mockTransformManager, mockQueueService, mockQueueWriter, mockRoninDomainResource)
        val transformedMessage =
            channel.sourceTransformer("name", message, mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId), emptyMap())
        assertEquals(fhirID, transformedMessage.dataMap[MirthKey.FHIR_ID.code])
        assertEquals(transformedResource, transformedMessage.message)
        unmockkConstructor(JsonMapper::class)
        unmockkObject(JacksonManager)
    }
}

class KafkaTestQueue(
    tenantService: TenantService,
    transformManager: TransformManager,
    queueService: KafkaQueueService,
    queueWriter: TenantlessQueueWriter<TestResource>,
    private val mockRoninDomainResource: TestResource = mockk()
) : KafkaQueue<TestResource>(tenantService, queueService, queueWriter) {
    override val resourceType: ResourceType = ResourceType.BUNDLE
    override val rootName: String = "Test"
    override val limit: Int = 5
    override fun deserializeAndTransform(string: String, tenant: Tenant): TestResource = mockRoninDomainResource
}

class KafkaTestQueueBad(
    tenantService: TenantService,
    transformManager: TransformManager,
    queueService: KafkaQueueService,
    queueWriter: TenantlessQueueWriter<TestResource>,
    private val mockRoninDomainResource: TestResource = mockk()
) : KafkaQueue<TestResource>(tenantService, queueService, queueWriter) {
    override val resourceType: ResourceType = ResourceType.BUNDLE
    override val rootName: String = "thisnameiscompletelyandutterlymcuhtoolongohno"
    override val limit: Int = 5
    override fun deserializeAndTransform(string: String, tenant: Tenant): TestResource = mockRoninDomainResource
}
