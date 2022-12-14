package com.projectronin.interop.mirth.channel.base

import com.fasterxml.jackson.databind.json.JsonMapper
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.queue.model.ApiMessage
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class KafkaQueueTest {
    private val tenantId = "tenant"
    private val mockTenant = mockk<Tenant>()
    private val mockRoninDomainResource = mockk<TestResource>()

    private lateinit var mockQueueService: KafkaQueueService
    private lateinit var mockVendorFactory: VendorFactory
    private lateinit var mockServiceFactory: ServiceFactory
    private lateinit var channel: KafkaTestQueue

    @BeforeEach
    fun setup() {
        mockQueueService = mockk()
        mockVendorFactory = mockk()
        mockServiceFactory = mockk {
            every { kafkaQueueService() } returns mockQueueService
            every { getTenant(tenantId) } returns mockTenant
            every { vendorFactory(mockTenant) } returns mockVendorFactory
        }
        channel = KafkaTestQueue(mockServiceFactory)
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
        val channel = KafkaTestQueue(mockServiceFactory)
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

        val channel = KafkaTestQueue(mockServiceFactory)
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

        val channel = KafkaTestQueue(mockServiceFactory, mockRoninDomainResource)
        val transformedMessage =
            channel.sourceTransformer("name", message, mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId), emptyMap())
        assertEquals(fhirID, transformedMessage.dataMap[MirthKey.FHIR_ID.code])
        assertEquals(transformedResource, transformedMessage.message)
        unmockkConstructor(JsonMapper::class)
        unmockkObject(JacksonManager)
    }

    @Test
    fun codeCov() {
        val channel = KafkaTestQueue(mockServiceFactory, mockRoninDomainResource)
        assertNotNull(channel.destinations["publish"])
        assertDoesNotThrow {
            channel.onDeploy("ronin-KafkaPatientQueue", emptyMap())
        }
        assertThrows<Exception> {
            channel.onDeploy("thisnameiscompletelyandutterlymcuhtoolongohno", emptyMap())
        }

        assertThrows<Exception> {
            KafkaTestQueueBad(
                mockServiceFactory,
                mockRoninDomainResource
            ).onDeploy("thisnameiscompletelyandutterlymcuhtoolongohno", emptyMap())
        }
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

        val channel = KafkaTestQueue(mockServiceFactory, mockRoninDomainResource)
        val transformedMessage =
            channel.sourceTransformer("name", message, mapOf(MirthKey.TENANT_MNEMONIC.code to tenantId), emptyMap())
        assertEquals(fhirID, transformedMessage.dataMap[MirthKey.FHIR_ID.code])
        assertEquals(transformedResource, transformedMessage.message)
        unmockkConstructor(JsonMapper::class)
        unmockkObject(JacksonManager)
    }
}

class KafkaTestQueue(
    serviceFactory: ServiceFactory,
    private val mockRoninDomainResource: TestResource = mockk()
) : KafkaQueue<TestResource>(serviceFactory, TestResource::class) {
    override val resourceType: ResourceType = ResourceType.BUNDLE
    override val rootName: String = "Test"
    override val limit: Int = 5
    override fun deserializeAndTransform(string: String, tenant: Tenant): TestResource = mockRoninDomainResource
}

class KafkaTestQueueBad(
    serviceFactory: ServiceFactory,
    private val mockRoninDomainResource: TestResource = mockk()
) : KafkaQueue<TestResource>(serviceFactory, TestResource::class) {
    override val resourceType: ResourceType = ResourceType.BUNDLE
    override val rootName: String = "thisnameiscompletelyandutterlymcuhtoolongohno"
    override val limit: Int = 5
    override fun deserializeAndTransform(string: String, tenant: Tenant): TestResource = mockRoninDomainResource
}
