package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.queue.QueueService
import org.springframework.stereotype.Component

@Component
class MDMQueueOut(
    private val queueService: QueueService,
    private val tenantConfigurationService: TenantConfigurationService,
) : ChannelService() {
    companion object : ChannelFactory<MDMQueueOut>()

    override val rootName = "MDMQueueOut"
    private val limit = 5
    override val destinations = emptyMap<String, DestinationService>()

    override fun channelSourceReader(
        tenantMnemonic: String,
        serviceMap: Map<String, Any>,
    ): List<MirthMessage> {
        val messages = queueService.dequeueHL7Messages(tenantMnemonic, MessageType.MDM, null, limit)
        return messages.map {
            MirthMessage(it.text)
        }
    }

    override fun channelOnDeploy(
        tenantMnemonic: String,
        serviceMap: Map<String, Any>,
    ): Map<String, Any> {
        val pair = tenantConfigurationService.getMDMInfo(tenantMnemonic)
        val address = pair?.first.toString()
        val port = pair?.second.toString()
        return mapOf(
            "PORT" to port,
            "ADDRESS" to address,
        ) + serviceMap
    }
}
