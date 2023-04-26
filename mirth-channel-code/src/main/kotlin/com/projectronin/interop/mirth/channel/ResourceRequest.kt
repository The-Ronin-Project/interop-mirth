package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaRequestService
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.ResourceRequestPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class ResourceRequest(
    private val kafkaRequestService: KafkaRequestService,
    resourceRequestPublish: ResourceRequestPublish
) : TenantlessSourceService() {
    override val destinations = mapOf("publish" to resourceRequestPublish)
    val groupId = "interop-mirth-resource_request_group"
    override val rootName = "ResourceRequest"

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(ResourceRequest::class.java)
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        val events = kafkaRequestService.retrieveRequestEvents(groupId)
        return events.map {
            MirthMessage(
                JacksonUtil.writeJsonValue(it),
                mapOf(
                    MirthKey.TENANT_MNEMONIC.code to it.tenantId
                )
            )
        }
    }
}
