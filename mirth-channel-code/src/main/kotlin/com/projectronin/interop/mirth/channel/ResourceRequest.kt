package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaRequestService
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.ResourceRequestPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.models.MirthMessage
import org.springframework.stereotype.Component

@Component
class ResourceRequest(
    private val kafkaRequestService: KafkaRequestService,
    resourceRequestPublish: ResourceRequestPublish
) : TenantlessSourceService() {
    override val destinations = mapOf("publish" to resourceRequestPublish)
    val groupId = "interop-mirth-resource_request_group"
    override val rootName = "ResourceRequest"

    companion object : ChannelConfiguration<ResourceRequest>() {
        override val channelClass = ResourceRequest::class
        override val id = "1c1cc5bc-bfca-4ff8-a7fd-099c3d8f4959"
        override val description =
            "Reads Kafka request events and writes to the appropriate load topic"
        override val metadataColumns: Map<String, String> = mapOf(
            "TENANT" to "tenantMnemonic",
            "ID" to "fhirID",
            "RESOURCE_TYPE" to "resourceType",
            "SOURCE" to "kafkaEventSource"
        )
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        val events = kafkaRequestService.retrieveRequestEvents(groupId)
        return events.map {
            MirthMessage(
                JacksonUtil.writeJsonValue(it),
                mapOf(
                    MirthKey.TENANT_MNEMONIC.code to it.tenantId,
                    MirthKey.FHIR_ID.code to it.resourceFHIRId,
                    MirthKey.RESOURCE_TYPE.code to it.resourceType
                )
            )
        }
    }
}
