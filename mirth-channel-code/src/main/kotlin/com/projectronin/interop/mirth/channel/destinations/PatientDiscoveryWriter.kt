package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import org.springframework.stereotype.Component

@Component
class PatientDiscoveryWriter(val kafkaLoadService: KafkaLoadService) : TenantlessDestinationService() {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val references = JacksonUtil.readJsonList(msg, String::class)
        // this could be benign, but likely something has gone wrong if we're finding no patients for a tenant
        if (references.isEmpty()) {
            return MirthResponse(
                MirthResponseStatus.ERROR,
                "No Patients found for tenant $tenantMnemonic"
            )
        }

        return try {
            val result = kafkaLoadService.pushLoadEvent(
                tenantMnemonic,
                DataTrigger.NIGHTLY,
                references.map { it.substringAfter("/") },
                ResourceType.PATIENT
            )
            val status = when (result.failures.isEmpty()) {
                true -> MirthResponseStatus.SENT
                false -> MirthResponseStatus.ERROR
            }
            MirthResponse(
                status,
                "Successes:  ${result.successful}\n" +
                    "Failures:  ${result.failures}",
                "${result.successful.size} successes, ${result.failures.size} failures"
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish to Kafka" }
            MirthResponse(
                MirthResponseStatus.ERROR,
                e.message
            )
        }
    }
}
