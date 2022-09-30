package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.connector.ServiceFactory

class PractitionerNightlyLoadWriter(rootName: String, serviceFactory: ServiceFactory) :
    DestinationService(rootName, serviceFactory) {

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        return publishTransformed(channelMap)
    }
}