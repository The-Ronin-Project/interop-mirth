package com.projectronin.interop.mirth.channels.client

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.KafkaRequestService
import com.projectronin.interop.kafka.client.KafkaClient
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.kafka.model.KafkaTopic
import com.projectronin.interop.kafka.spring.AdminWrapper
import com.projectronin.interop.kafka.spring.KafkaBootstrapConfig
import com.projectronin.interop.kafka.spring.KafkaCloudConfig
import com.projectronin.interop.kafka.spring.KafkaConfig
import com.projectronin.interop.kafka.spring.KafkaPropertiesConfig
import com.projectronin.interop.kafka.spring.KafkaPublishConfig
import com.projectronin.interop.kafka.spring.KafkaRetrieveConfig
import com.projectronin.interop.kafka.spring.KafkaSaslConfig
import com.projectronin.interop.kafka.spring.KafkaSaslJaasConfig
import com.projectronin.interop.kafka.spring.KafkaSecurityConfig
import com.projectronin.interop.kafka.spring.LoadSpringConfig
import com.projectronin.interop.kafka.spring.PublishSpringConfig
import com.projectronin.interop.kafka.spring.RequestSpringConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.common.ConsumerGroupState
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object KafkaClient {
    private val mutex = Mutex()
    private val logger = KotlinLogging.logger { }

    private val config = KafkaConfig(
        cloud = KafkaCloudConfig(
            vendor = "oci",
            region = "us-phoenix-1"
        ),
        bootstrap = KafkaBootstrapConfig("localhost:9092"),
        publish = KafkaPublishConfig("interop-kafka-mirth-it"),
        retrieve = KafkaRetrieveConfig("interop-kafka-mirth-it"),
        properties = KafkaPropertiesConfig(
            security = KafkaSecurityConfig(protocol = "PLAINTEXT"),
            sasl = KafkaSaslConfig(
                mechanism = "GSSAPI",
                jaas = KafkaSaslJaasConfig("nothing")
            )
        )
    )
    private val adminWrapper = AdminWrapper(config)
    val adminClient = adminWrapper.client
    val client = KafkaClient(config, adminWrapper)
    private val loadSpringConfig = LoadSpringConfig(config)
    val kafkaLoadService = KafkaLoadService(client, loadSpringConfig.loadTopics())
    private val publishSpringConfig = PublishSpringConfig(config)
    val kafkaPublishService = KafkaPublishService(client, publishSpringConfig.publishTopics())
    val requestSpringConfig = RequestSpringConfig(config)
    val kafkaRequestService = KafkaRequestService(client, requestSpringConfig.requestTopic())

    fun reset() {
        runBlocking {
            mutex.withLock {
                val consumerGroups = adminClient.listConsumerGroups().all().get()
                val groupIds = consumerGroups.map { it.groupId() }.toSet()
                groupIds.map {
                    runCatching {
                        val offsets = adminClient.listConsumerGroupOffsets(it).partitionsToOffsetAndMetadata().get()
                        val recordsToDelete = offsets.mapValues { RecordsToDelete.beforeOffset(it.value.offset()) }
                        runCatching { adminClient.deleteRecords(recordsToDelete).all().get() }
                    }
                }
            }
        }
    }

    fun ensureStability(topic: String) {
        var count = 0
        runBlocking {
            logger.info { "Ensuring stability on $topic" }
            mutex.withLock {
                while (true) {
                    val names = adminClient.listTopics().names().get()
                    if (names.any { it == topic }) {
                        val groups = adminClient.listConsumerGroups().valid().get()
                        val unstableGroups = groups.filter {
                            val state = it.state().get()
                            state != ConsumerGroupState.EMPTY && state != ConsumerGroupState.STABLE
                        }
                        if (unstableGroups.isEmpty()) {
                            logger.info { "Topic created and consumers are all stable" }
                            break
                        } else {
                            // someone is unstable, are they subscribed to the topic we care about?
                            val unstableIds = unstableGroups.map { it.groupId() }
                            val unstableAssignedTopics =
                                adminClient.describeConsumerGroups(unstableIds).all().get().entries.map {
                                    it.value.members().map { member ->
                                        member.assignment().topicPartitions().map { topicPartition ->
                                            topicPartition.topic()
                                        }
                                    }.flatten()
                                }.flatten().distinct()
                            if (!unstableAssignedTopics.contains(topic)) {
                                break
                            }
                            logger.info { "Topic created and all relevant consumers are stable" }
                        }
                    } else {
                        logger.info { "Topic not yet found" }
                    }
                    count += 1
                    if (count > 6000) throw IllegalStateException("Waited for 10 minutes for Kafka Rebalance")
                    if (count % 100 == 0) logger.info { "." }
                    Thread.sleep(100)
                }
            }
            logger.info { "Stability ensured on $topic" }
        }
    }

    fun publishTopics(resourceType: ResourceType): List<KafkaTopic> =
        publishSpringConfig.publishTopics().filter { it.resourceType == resourceType }

    fun loadTopic(resourceType: ResourceType): KafkaTopic =
        loadSpringConfig.loadTopics().first { it.resourceType == resourceType }

    fun pushLoadEvent(
        tenantId: String,
        trigger: DataTrigger,
        resourceFHIRIds: List<String>,
        resourceType: ResourceType,
        metadata: Metadata = Metadata(
            runId = UUID.randomUUID().toString(),
            runDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        ),
        flowOptions: InteropResourceLoadV1.FlowOptions? = null
    ) {
        val topic = loadTopic(resourceType)
        runCatching {
            adminClient.createTopics(listOf(NewTopic(topic.topicName, 1, 1))).all().get()
        }
        ensureStability(topic.topicName)
        kafkaLoadService.pushLoadEvent(
            tenantId = tenantId,
            trigger = trigger,
            resourceFHIRIds = resourceFHIRIds,
            resourceType = resourceType,
            metadata = metadata,
            flowOptions = flowOptions
        )
    }

    fun pushPublishEvent(
        tenantId: String,
        trigger: DataTrigger,
        resources: List<Resource<*>>,
        metadata: Metadata = Metadata(
            runId = UUID.randomUUID().toString(),
            runDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        )
    ) {
        val topics = publishSpringConfig.publishTopics().filter {
            it.resourceType.name == resources.first().resourceType
        }

        val relevantTopic = when (trigger) {
            DataTrigger.NIGHTLY -> topics.first { it.topicName.contains("nightly") }
            DataTrigger.AD_HOC -> topics.first { it.topicName.contains("adhoc") }
        }
        runCatching {
            adminClient.createTopics(listOf(NewTopic(relevantTopic.topicName, 1, 1))).all().get()
        }
        ensureStability(relevantTopic.topicName)
        kafkaPublishService.publishResources(
            tenantId = tenantId,
            trigger = trigger,
            resources = resources,
            metadata = metadata
        )
    }
}
