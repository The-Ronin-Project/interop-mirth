package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.client.KafkaClient
import com.projectronin.interop.kafka.model.DataTrigger
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

object KafkaWrapper {
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
    private val client = KafkaClient(config, AdminWrapper(config))

    val kafkaLoadService = KafkaLoadService(client, LoadSpringConfig().loadTopics())
    val kafkaPublishService = KafkaPublishService(client, PublishSpringConfig().publishTopics())

    // publish events
    fun validatePublishEvents(
        number: Int,
        resourceType: ResourceType,
        dataTrigger: DataTrigger,
        groupId: String? = null
    ): Boolean {
        var count = 0
        repeat(5) {
            count += kafkaPublishService.retrievePublishEvents(resourceType, dataTrigger, groupId).size

            if (count == number) return true
            runBlocking { delay(2000) }
        }
        KotlinLogging.logger { }.error { "Expected $number, was $count" }
        return false
    }

    // load events
    fun validateLoadEvents(
        number: Int,
        resourceType: ResourceType,
        groupId: String? = null
    ): Boolean {
        var count = 0
        repeat(5) {
            count += kafkaLoadService.retrieveLoadEvents(resourceType, groupId).size
            KotlinLogging.logger { }.warn { "Count: $count" }
            if (count == number) return true
            runBlocking { delay(1000) }
        }
        KotlinLogging.logger { }.error { "Expected $number, was $count" }
        return false
    }
}
