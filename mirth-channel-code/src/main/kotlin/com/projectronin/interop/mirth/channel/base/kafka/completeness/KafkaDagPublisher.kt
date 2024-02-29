package com.projectronin.interop.mirth.channel.base.kafka.completeness

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.completeness.topics.CompletenessKafkaTopicConfig
import com.projectronin.interop.kafka.client.KafkaClient
import com.projectronin.interop.kafka.model.Failure
import com.projectronin.interop.kafka.model.KafkaAction
import com.projectronin.interop.kafka.model.KafkaEvent
import com.projectronin.interop.kafka.model.KafkaTopic
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.json.eventinteropcompleteness.v1.DagRegistrationV1Schema
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class KafkaDagPublisher(
    private val kafkaClient: KafkaClient,
    completenessKafkaTopicConfig: CompletenessKafkaTopicConfig,
    @Value("\${completeness.enabled:true}")
    private val dagRegistrationEnabled: Boolean,
) {
    private val topic: KafkaTopic = completenessKafkaTopicConfig.dagRegistration()
    protected val logger = KotlinLogging.logger(this::class.java.name)

    fun publishDag(
        resourceType: ResourceType,
        consumedResourceTypes: List<ResourceType>,
    ): PushResponse<KafkaEvent<DagRegistrationV1Schema>> {
        if (!dagRegistrationEnabled) {
            logger.debug { "Skipping publishing DAG event with resource $resourceType" }
            return PushResponse()
        }

        logger.debug { "Generating DAG event with resource $resourceType and subscriptions $consumedResourceTypes" }
        val dag =
            DagRegistrationV1Schema().apply {
                resource = com.projectronin.json.eventinteropcompleteness.ResourceType.fromValue(resourceType.name)
                consumedResources =
                    consumedResourceTypes.map { com.projectronin.json.eventinteropcompleteness.ResourceType.fromValue(it.name) }
            }

        logger.debug { "Publishing DAG event $dag" }
        val event =
            KafkaEvent(
                domain = "interop-completeness",
                resource = "dag",
                action = KafkaAction.PUBLISH,
                resourceId = resourceType.name,
                data = dag,
            )

        val response =
            runCatching {
                kafkaClient.publishEvents(topic, listOf(event))
            }.fold(
                onSuccess = { response ->
                    logger.info { "Successfully published DAG $dag" }
                    response
                },
                onFailure = { exception ->
                    logger.error(exception) { "Exception while attempting to publish DAG $dag to $topic" }
                    PushResponse(failures = listOf(Failure(event, exception)))
                },
            )

        return response
    }
}
