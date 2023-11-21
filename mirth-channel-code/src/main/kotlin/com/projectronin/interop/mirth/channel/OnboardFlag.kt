package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaPatientOnboardService
import com.projectronin.interop.kafka.PatientOnboardingStatus
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.base.TenantlessSourceService
import com.projectronin.interop.mirth.channel.destinations.OnboardFlagWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.models.MirthMessage
import org.springframework.stereotype.Component

@Component
class OnboardFlag(
    private val kafkaPatientOnboardService: KafkaPatientOnboardService,
    destination: OnboardFlagWriter
) : TenantlessSourceService() {
    override val rootName: String = "OnboardFlag"
    override val destinations: Map<String, TenantlessDestinationService> = mapOf("onboardFlagWriter" to destination)

    companion object : ChannelConfiguration<OnboardFlag>() {
        override val channelClass = OnboardFlag::class
        override val id = "54c15c0b-2ab9-46af-b7db-278ba2c02bb8"
        override val description =
            "Reads Kafka events and sets a flag in the EHR marking the patient as having been onboarded in Ronin."
        override val metadataColumns: Map<String, String> = mapOf(
            "TENANT" to "tenantMnemonic",
            "PATIENT_ID" to "fhirID"
        )

        override val daysUntilPruned: Int = 60
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
