package com.projectronin.interop.mirth.channel.destinations
import com.projectronin.event.interop.resource.request.v1.InteropResourceRequestV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.stereotype.Component

@Component
class ResourceRequestPublish(
    private val kafkaLoadService: KafkaLoadService,
    private val tenantService: TenantService
) : TenantlessDestinationService() {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        // just ensure the tenant ID we got was valid
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")

        val requestEvent = JacksonUtil.readJsonObject(msg, InteropResourceRequestV1::class)

        val resourceType = ResourceType.valueOf(requestEvent.resourceType)
        val result = kafkaLoadService.pushLoadEvent(
            tenantId = tenant.mnemonic,
            resourceType = resourceType,
            resourceFHIRIds = listOf(requestEvent.resourceFHIRId),
            trigger = DataTrigger.AD_HOC
        )

        return if (result.failures.isNotEmpty()) {
            MirthResponse(
                status = MirthResponseStatus.ERROR,
                JacksonUtil.writeJsonValue(result.failures),
                "Failed to publish to Load Topic"
            )
        } else {
            MirthResponse(
                status = MirthResponseStatus.SENT,
                JacksonUtil.writeJsonValue(result.successful),
                "Published to Load Topic"
            )
        }
    }
}
