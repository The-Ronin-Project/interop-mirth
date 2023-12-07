package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.mirth.service.TenantConfigurationService
import mu.KotlinLogging

/**
 * Util to filter out events whose resources are blocked based on the resource tenant
 * resource-publish and resource-load events will need to be filtered
 * Both functions only return events whose resources are NOT blocked.
 * Events whose resources are blocked are logged and then tossed out.
 */
private val logger = KotlinLogging.logger { }

fun filterBlockedLoadEvents(
    channelResourceType: ResourceType,
    events: List<InteropResourceLoadV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourceLoadV1> {
    val resources = mutableListOf<InteropResourceLoadV1>()
    events.forEach {
        val blockedResourceList = tenantConfigService.getConfiguration(it.tenantId).blockedResources?.split(",")
        if (blockedResourceList?.isNotEmpty() == true && channelResourceType.toString() in blockedResourceList) {
            logger.debug { "resource ${it.resourceType} is blocked for ${it.tenantId}" }
        } else {
            resources.add(it)
        }
    }
    return resources
}

fun filterBlockedPublishedEvents(
    channelResourceType: ResourceType,
    events: List<InteropResourcePublishV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourcePublishV1> {
    val resources = mutableListOf<InteropResourcePublishV1>()
    events.forEach {
        val blockedResourceList = tenantConfigService.getConfiguration(it.tenantId).blockedResources?.split(",")
        if (blockedResourceList?.isNotEmpty() == true && channelResourceType.toString() in blockedResourceList) {
            logger.debug { "resource ${it.resourceType} is blocked for ${it.tenantId}" }
        } else {
            resources.add(it)
        }
    }
    return resources
}
