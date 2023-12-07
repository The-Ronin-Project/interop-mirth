package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.channel.util.getMetadata
import org.springframework.stereotype.Component

@Component
class PatientDiscoveryWriter(val kafkaLoadService: KafkaLoadService) : TenantlessDestinationService() {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthResponse {
        val references = JacksonUtil.readJsonList(msg, String::class)
        // no patients found for a given location, log an error and move on
        if (references.isEmpty()) {
            logger.warn { "No Patients found for tenant $tenantMnemonic" }
            return MirthResponse(
                MirthResponseStatus.SENT,
                "No Patients found for tenant $tenantMnemonic",
            )
        }
        val metadata = getMetadata(sourceMap)
        val dataTrigger =
            when (metadata.backfillRequest) {
                null -> DataTrigger.NIGHTLY
                else -> DataTrigger.BACKFILL
            }

        return try {
            val result =
                kafkaLoadService.pushLoadEvent(
                    tenantMnemonic,
                    dataTrigger,
                    references.map { it.substringAfter("/") },
                    ResourceType.Patient,
                    metadata,
                )
            val status =
                when (result.failures.isEmpty()) {
                    true -> MirthResponseStatus.SENT
                    false -> MirthResponseStatus.ERROR
                }
            MirthResponse(
                status,
                "Successes:  ${result.successful}\n" +
                    "Failures:  ${result.failures}",
                "${result.successful.size} successes, ${result.failures.size} failures",
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish to Kafka" }
            MirthResponse(
                MirthResponseStatus.ERROR,
                e.message,
            )
        }
    }
}
