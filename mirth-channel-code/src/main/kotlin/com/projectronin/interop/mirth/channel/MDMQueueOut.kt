package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.hl7.MessageType
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.base.MirthDestination
import com.projectronin.interop.mirth.models.Datatype
import com.projectronin.interop.mirth.models.MirthMessage
import com.projectronin.interop.mirth.models.destination.DestinationConfiguration
import com.projectronin.interop.mirth.models.destination.MLLPDestinationConfiguration
import com.projectronin.interop.mirth.models.polling.IntervalPollingConfig
import com.projectronin.interop.mirth.models.polling.PollingConfig
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.queue.QueueService
import org.springframework.stereotype.Component

@Component
class MDMQueueOut(
    private val queueService: QueueService,
    private val tenantConfigurationService: TenantConfigurationService
) : ChannelService() {
    companion object : ChannelConfiguration<MDMQueueOut>() {
        override val channelClass = MDMQueueOut::class
        override val id = "50968316-2e13-41f7-b5dd-1d6a46d17c62"
        override val description = "Reads MDM HL7 Messages off the HL7 Queue. Publishes them to Tenant's Server"
        override val metadataColumns: Map<String, String> = mapOf(
            "SOURCE" to "mirth_source",
            "TYPE" to "mirth_type"
        )

        override val datatype: Datatype = Datatype.HL7V2
        override val pollingConfig: PollingConfig = IntervalPollingConfig(
            pollingFrequency = 300_000
        )
        override val daysUntilPruned: Int = 60
        override val storeAttachments: Boolean = true
    }

    override val rootName = "MDMQueueOut"
    private val limit = 5
    override val destinations = emptyMap<String, MirthDestination>()

    private val hl7Destination = MLLPDestinationConfiguration(
        name = "HL7 OUT",
        threadCount = 1,
        queueEnabled = false
    )

    override fun getNonJavascriptDestinations(): List<DestinationConfiguration> = listOf(hl7Destination)

    override fun channelSourceReader(
        tenantMnemonic: String,
        serviceMap: Map<String, Any>
    ): List<MirthMessage> {
        val messages = queueService.dequeueHL7Messages(tenantMnemonic, MessageType.MDM, null, limit)
        return messages.map {
            MirthMessage(it.text)
        }
    }

    override fun channelOnDeploy(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> {
        val pair = tenantConfigurationService.getMDMInfo(tenantMnemonic)
        val address = pair?.first.toString()
        val port = pair?.second.toString()
        return mapOf(
            "PORT" to port,
            "ADDRESS" to address
        ) + serviceMap
    }
}
