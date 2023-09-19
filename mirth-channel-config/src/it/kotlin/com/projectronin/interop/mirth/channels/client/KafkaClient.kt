package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.kafka.testing.client.KafkaTestingClient

object KafkaClient {
    val testingClient = KafkaTestingClient("localhost:9092")
}
