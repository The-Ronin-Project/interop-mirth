package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.PatientPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.stereotype.Component

@Component
class PatientLoad(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val kafkaLoadService: KafkaLoadService,
    patientPublish: PatientPublish
) : TenantlessSourceService() {
    override val rootName = "PatientLoad"
    override val destinations = mapOf("publish" to patientPublish)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(PatientLoad::class.java)
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
        val events = kafkaLoadService.retrieveLoadEvents(resourceType = ResourceType.PATIENT)
        return events.map { event ->
            MirthMessage(
                event.resourceFHIRId,
                mapOf(
                    MirthKey.TENANT_MNEMONIC.code to event.tenantId,
                    MirthKey.RESOURCE_TYPE.code to "Patient",
                    MirthKey.FHIR_ID.code to event.resourceFHIRId,
                    MirthKey.DATA_TRIGGER.code to when (event.dataTrigger) {
                        InteropResourceLoadV1.DataTrigger.adhoc -> DataTrigger.AD_HOC
                        InteropResourceLoadV1.DataTrigger.backfill -> DataTrigger.AD_HOC
                        InteropResourceLoadV1.DataTrigger.nightly -> DataTrigger.NIGHTLY
                        else -> {}
                    }
                )
            )
        }
    }

    override fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val patient = vendorFactory.patientService.getPatient(tenant, msg)
        return MirthMessage(
            JacksonUtil.writeJsonValue(patient),
            mapOf(
                MirthKey.TENANT_MNEMONIC.code to tenant.mnemonic,
                MirthKey.RESOURCE_TYPE.code to "Patient",
                MirthKey.FHIR_ID.code to patient.id!!.value!!,
                MirthKey.DATA_TRIGGER.code to sourceMap[MirthKey.DATA_TRIGGER.code] as DataTrigger
            )
        )
    }
}
