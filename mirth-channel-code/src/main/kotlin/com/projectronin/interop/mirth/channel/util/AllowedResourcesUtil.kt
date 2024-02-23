package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.mirth.service.TenantConfigurationService

/**
 * Util to filter out events whose resources are allowed/targetResource based on the event metadata
 * if the targeted resource list is empty, check the blocked resources on the tenant config
 */

fun filterAllowedLoadEventsResources(
    channelResourceType: ResourceType,
    events: List<InteropResourceLoadV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourceLoadV1> {
    val groupedEvents = events.groupBy { it.tenantId }
    // group by tenant
    return groupedEvents.mapValues { (_, event) ->
        // filter based on targeted or blocked
        event.filter {
            val isExplicitlyTargeted = it.metadata.targetedResources?.contains(channelResourceType.toString()) == true
            val isImplicitlyTargeted = it.metadata.targetedResources?.isEmpty() == true

            // get blocked resources from tenant config
            val blockedResourceList = tenantConfigService.getConfiguration(it.tenantId).blockedResources?.split(",")
            // check for channelResourceType in the blocked resource list
            val isBlocked =
                blockedResourceList?.isNotEmpty() == true &&
                    channelResourceType.toString() in blockedResourceList
            // we want to allow an event through even if we're blocking if it's in the allowed list
            // an empty allowed list in the future means "all resources are allowed"
            // but it could also mean right now "we're not populating it", so for now we'll check if those are blocked
            isExplicitlyTargeted || isImplicitlyTargeted && !isBlocked
        }
    }.values.flatten()
}

fun filterAllowedPublishedResources(
    channelResourceType: ResourceType,
    events: List<InteropResourcePublishV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourcePublishV1> {
    val groupedEvents = events.groupBy { it.tenantId }
    // group by tenant
    return groupedEvents.mapValues { (_, event) ->
        // filter based on targeted or blocked
        event.filter {
            val isExplicitlyTargeted = it.metadata.targetedResources?.contains(channelResourceType.toString()) == true
            val isImplicitlyTargeted = it.metadata.targetedResources?.isEmpty() == true

            // get blocked resources from tenant config
            val blockedResourceList = tenantConfigService.getConfiguration(it.tenantId).blockedResources?.split(",")
            // check for channelResourceType in the blocked resource list
            val isBlocked =
                blockedResourceList?.isNotEmpty() == true &&
                    channelResourceType.toString() in blockedResourceList
            // we want to allow an event through even if we're blocking if it's in the allowed list
            // an empty allowed list in the future means "all resources are allowed"
            // but it could also mean right now "we're not populating it", so for now we'll check if those are blocked
            isExplicitlyTargeted || isImplicitlyTargeted && !isBlocked
        }
    }.values.flatten()
}
