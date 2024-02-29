package com.projectronin.interop.mirth.channel.base.kafka.completeness

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.completeness.topics.CompletenessKafkaTopicConfig
import com.projectronin.interop.kafka.client.KafkaClient
import com.projectronin.interop.kafka.model.KafkaEvent
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.interop.kafka.spring.KafkaConfig
import com.projectronin.json.eventinteropcompleteness.v1.DagRegistrationV1Schema
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KafkaDagPublisherTest {
    lateinit var kafkaConfig: KafkaConfig
    lateinit var kafkaClient: KafkaClient
    lateinit var completenessKafkaTopicConfig: CompletenessKafkaTopicConfig
    lateinit var kafkaDagPublisher: KafkaDagPublisher

    @BeforeEach
    fun setup() {
        kafkaConfig =
            mockk {
                every { cloud.region } returns "us-east"
                every { cloud.vendor } returns "oci"
                every { retrieve.serviceId } returns "service-id"
            }
        kafkaClient = mockk()
        completenessKafkaTopicConfig = CompletenessKafkaTopicConfig(kafkaConfig)
        kafkaDagPublisher = KafkaDagPublisher(kafkaClient, completenessKafkaTopicConfig, true)
    }

    @AfterEach
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `test publishing dag`() {
        every { kafkaClient.publishEvents<DagRegistrationV1Schema>(any(), any()) } answers {
            val events = it.invocation.args[1] as List<KafkaEvent<DagRegistrationV1Schema>>
            PushResponse(successful = events)
        }
        val response = kafkaDagPublisher.publishDag(ResourceType.Condition, listOf(ResourceType.Patient))

        assertEquals(0, response.failures.size)
        assertEquals(1, response.successful.size)
        val event = response.successful[0]
        assertEquals("ronin.interop-completeness.dag.publish", event.type)
        assertEquals("ronin.interop-completeness.dag/Condition", event.subject)
        val data = event.data
        assertEquals("Condition", data.resource.value())
        assertEquals(1, data.consumedResources.size)
        assertEquals("Patient", data.consumedResources[0].value())
    }

    @Test
    fun `test publishing dag catches errors`() {
        every {
            kafkaClient.publishEvents<DagRegistrationV1Schema>(
                any(),
                any(),
            )
        } throws RuntimeException("Cannot send to Kafka")
        val response = kafkaDagPublisher.publishDag(ResourceType.Condition, listOf(ResourceType.Patient))

        assertEquals(1, response.failures.size)
        assertEquals(0, response.successful.size)
        val failure = response.failures[0]
        val error = failure.error
        assertEquals("Cannot send to Kafka", error.message)
        val event = failure.data
        assertEquals("ronin.interop-completeness.dag.publish", event.type)
        assertEquals("ronin.interop-completeness.dag/Condition", event.subject)
        val data = event.data
        assertEquals("Condition", data.resource.value())
        assertEquals(1, data.consumedResources.size)
        assertEquals("Patient", data.consumedResources[0].value())
    }

    @Test
    fun `test disabling dag registration publisher`() {
        kafkaDagPublisher = KafkaDagPublisher(kafkaClient, completenessKafkaTopicConfig, false)
        val response = kafkaDagPublisher.publishDag(ResourceType.Condition, listOf(ResourceType.Patient))

        assertEquals(0, response.failures.size)
        assertEquals(0, response.successful.size)

        verify(exactly = 0) {
            kafkaClient.publishEvents(any(), any<List<KafkaEvent<DagRegistrationV1Schema>>>())
        }
    }
}
