package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import org.springframework.stereotype.Component

@Component
class PatientPublish(
    private val tenantService: TenantService,
    private val publishService: PublishService,
    private val transformManager: TransformManager,
    private val roninPatient: RoninPatient
) : TenantlessDestinationService() {
    val type = Patient::class

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val resource = JacksonUtil.readJsonObject(msg, type)
        val resourceList = listOf(resource)
        val resourceType = type.simpleName
        if (!publishService.publishFHIRResources(
                tenantMnemonic,
                resourceList,
                sourceMap[MirthKey.DATA_TRIGGER.code] as DataTrigger
            )
        ) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = JacksonUtil.writeJsonValue(resourceList),
                message = "Failed to publish $resourceType(s)"
            )
        }
        return MirthResponse(
            status = MirthResponseStatus.SENT,
            detailedMessage = JacksonUtil.writeJsonValue(resourceList),
            message = "Published ${resourceList.size} $resourceType(s)",
            dataMap = emptyMap()
        )
    }

    override fun channelDestinationTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val tenant =
            tenantService.getTenantForMnemonic(tenantMnemonic)
                ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val patient = JacksonUtil.readJsonObject(msg, type)
        val transformed = transformManager.transformResource(patient, roninPatient, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Patient for tenant ${tenant.mnemonic}")

        return MirthMessage(
            message = JacksonUtil.writeJsonValue(transformed),
            dataMap = mapOf(
                MirthKey.FHIR_ID.code to (transformed.id!!.value!!),
                MirthKey.TENANT_MNEMONIC.code to tenantMnemonic,
                MirthKey.DATA_TRIGGER.code to sourceMap[MirthKey.DATA_TRIGGER.code] as DataTrigger
            )
        )
    }
}
