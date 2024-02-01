package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.KafkaPublishService
import com.projectronin.interop.mirth.channel.base.kafka.KafkaTopicReader
import com.projectronin.interop.mirth.channel.destinations.DiagnosticReportPublish
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.mirth.spring.SpringUtil
import org.springframework.stereotype.Component

@Component
class DiagnosticReportLoad(
    kafkaPublishService: KafkaPublishService,
    kafkaLoadService: KafkaLoadService,
    override val tenantConfigService: TenantConfigurationService,
    defaultPublisher: DiagnosticReportPublish,
) : KafkaTopicReader(kafkaPublishService, kafkaLoadService, defaultPublisher) {
    override val rootName = "DiagnosticReportLoad"
    override val channelGroupId = "interop-mirth-diagnostic-report_group"
    override val publishedResourcesSubscriptions = listOf(ResourceType.Patient)
    override val resource = ResourceType.DiagnosticReport
    override val publishEventOverrideResources = listOf(ResourceType.Patient)

    companion object {
        fun create() = SpringUtil.applicationContext.getBean(DiagnosticReportLoad::class.java)
    }
}
