package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader

abstract class LoadChannelConfiguration<T : KafkaTopicReader> : ChannelConfiguration<T>() {
    override val metadataColumns: Map<String, String> = mapOf(
        "TENANT" to "tenantMnemonic",
        "RUN" to "kafkaEventRunId",
        "EVENT" to "kafkaEvent",
        "FAILED" to "failureCount",
        "SUCCEEDED" to "resourceCount",
        "SOURCE" to "kafkaEventSource"
    )
}
