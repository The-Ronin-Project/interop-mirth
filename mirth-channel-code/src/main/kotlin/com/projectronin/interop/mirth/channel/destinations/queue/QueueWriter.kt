package com.projectronin.interop.mirth.channel.destinations.queue

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.DomainResource
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import kotlin.reflect.KClass

/**
 *  This destination should be able to used for any publish action that's just taking a RoninDomainResource object
 *  and publishing it to the Ronin clinical data store
 */
abstract class QueueWriter<T : DomainResource<T>>(
    tenantService: TenantService,
    transformManager: TransformManager,
    publishService: PublishService,
    private val type: KClass<out T>
) : DestinationService(tenantService, transformManager, publishService) {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val resource = JacksonManager.objectMapper.readValue(msg, type.java)
        return publishResources(tenantMnemonic, listOf(resource), resource::class.simpleName)
    }
}
