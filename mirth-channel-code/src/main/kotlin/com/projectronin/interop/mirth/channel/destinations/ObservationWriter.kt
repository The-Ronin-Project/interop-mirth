package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.stereotype.Component

@Component
class ObservationWriter(
    tenantService: TenantService,
    transformManager: TransformManager,
    publishService: PublishService
) :
    DestinationService(tenantService, transformManager, publishService) {

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        return publishTransformed(sourceMap, channelMap, "Observation")
    }
}
