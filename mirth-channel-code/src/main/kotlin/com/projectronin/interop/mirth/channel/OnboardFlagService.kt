package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaPatientOnboardService
import com.projectronin.interop.kafka.PatientOnboardingStatus
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.OnboardFlagWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class OnboardFlagService(
    private val kafkaPatientOnboardService: KafkaPatientOnboardService,
    destination: OnboardFlagWriter
) : TenantlessSourceService() {
    override val rootName: String = "OnboardFlag"
    override val destinations: Map<String, TenantlessDestinationService> = mapOf("onboardFlagWriter" to destination)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(OnboardFlagService::class.java)
    }

    override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> =
        kafkaPatientOnboardService.retrieveOnboardEvents(
            "interop-mirth-onboard_group"
        ).filter { it.action == PatientOnboardingStatus.OnboardAction.ONBOARD }
            .map {
                MirthMessage(
                    JacksonUtil.writeJsonValue(it),
                    mapOf(MirthKey.TENANT_MNEMONIC.code to it.tenantId, MirthKey.FHIR_ID.code to it.patientId)
                )
            }
}
