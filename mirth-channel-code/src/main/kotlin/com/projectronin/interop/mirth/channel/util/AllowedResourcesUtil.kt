package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.mirth.service.TenantConfigurationService

/**
 * Util to filter out events whose resources are allowed/targetResource based on the event metadata
 * if the targeted resource list is empty, check the blocked resources on the tenant config
 *
 * we want to allow an event through even if we're blocking, if it's in the allowed list
 * an empty allowed list in the future means "all resources are allowed"
 * but it could also mean right now "we're not populating it", so for now we'll check if those are blocked
 */

fun filterAllowedLoadEventsResources(
    channelResourceType: ResourceType,
    events: List<InteropResourceLoadV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourceLoadV1> {
    // group by tenant
    val groupedEvents = events.groupBy { it.tenantId }
    return groupedEvents.mapValues { (_, eventByTenant) ->
        // filter based on targeted or blocked
        // grab the resource list here because it's the same by tenant and we can avoid a db call
        val blockedResourceList = tenantConfigService.getConfiguration(eventByTenant.first().tenantId).blockedResources?.split(",")

        eventByTenant.filter {
            // we've actually found our resource in the targetedResourceList
            val isExplicitlyTargeted = it.metadata.targetedResources?.contains(channelResourceType.toString()) == true

            // either there is no list or it's empty
            val isImplicitlyTargeted = it.metadata.targetedResources.isNullOrEmpty()

            // we've deliberately blocked this resource type in our old tenant config
            val isBlocked =
                !blockedResourceList.isNullOrEmpty() &&
                    channelResourceType.toString() in blockedResourceList

            isExplicitlyTargeted ||
                (isImplicitlyTargeted && !isBlocked)
        }
    }.values.flatten()
}

fun filterAllowedPublishedResources(
    channelResourceType: ResourceType,
    events: List<InteropResourcePublishV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourcePublishV1> {
    // group by tenant
    val groupedEvents = events.groupBy { it.tenantId }

    return groupedEvents.mapValues { (_, eventByTenant) ->
        // filter based on targeted or blocked
        // grab the resource list here because it's the same by tenant and we can avoid a db call
        val blockedResourceList = tenantConfigService.getConfiguration(eventByTenant.first().tenantId).blockedResources?.split(",")

        eventByTenant.filter {
            // we've actually found our resource in the targetedResourceList
            val isExplicitlyTargeted = it.metadata.targetedResources?.contains(channelResourceType.toString()) == true
            // either there is no list or it's empty
            val isImplicitlyTargeted = it.metadata.targetedResources.isNullOrEmpty()

            // we've deliberately blocked this resource type in our old tenant config
            val isBlocked =
                !blockedResourceList.isNullOrEmpty() &&
                    channelResourceType.toString() in blockedResourceList
            isExplicitlyTargeted ||
                (isImplicitlyTargeted && !isBlocked)
        }
    }.values.flatten()
}
