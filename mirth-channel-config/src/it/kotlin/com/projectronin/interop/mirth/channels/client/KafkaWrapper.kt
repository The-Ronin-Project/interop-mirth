package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.kafka.client.KafkaClient
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
                jaas = KafkaSaslJaasConfig("nothing"),
            )
        )
    )
    private val client = KafkaClient(config)

    val kafkaLoadService = KafkaLoadService(client, LoadSpringConfig().loadTopics())
    val kafkaPublishService = KafkaPublishService(client, PublishSpringConfig().publishTopics())
}
