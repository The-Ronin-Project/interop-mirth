package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.mirth.service.TenantConfigurationService

/**
 * Util to filter out events whose resources are allowed/targetResource based on the event metadata
 * if the targeted resource list is empty, check the blocked resources on the tenant config
 */

fun filterAllowedLoadEventsResources(
    events: List<InteropResourceLoadV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourceLoadV1> {
    // if events is empty just return it
    if (events.isEmpty()) return events
    val allowedResources = mutableListOf<InteropResourceLoadV1>()
    events.forEach {
        // if the event targeted resources is empty, call blocked resources and return
        if (it.metadata.targetedResources?.isEmpty() == true) {
            filterBlockedLoadEvents(
                it,
                tenantConfigService,
            )?.let { resource -> allowedResources.add(resource) }
        } else if (it.metadata.targetedResources?.contains(it.resourceType.toString()) == true) {
            // if the targeted resources is not empty, check that the resource type is in the targeted resources
            allowedResources.add(it)
        } else {
            allowedResources.add(it)
        }
    }

    return if (allowedResources.isNotEmpty()) {
        allowedResources
    } else {
        emptyList()
    }
}

fun filterAllowedPublishedResources(
    events: List<InteropResourcePublishV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourcePublishV1> {
    if (events.isEmpty()) return events
    val allowedResources = mutableListOf<InteropResourcePublishV1>()
    events.forEach {
        // if the event targeted resources is empty, call blocked resources and return
        if (it.metadata.targetedResources?.isEmpty() == true) {
            filterBlockedPublishedEvents(
                it,
                tenantConfigService,
            )?.let { resource -> allowedResources.add(resource) }
        } else if (it.metadata.targetedResources?.contains(it.resourceType.toString()) == true) {
            // if the targeted resources is not empty, check that the resource type is in the targeted resource
            allowedResources.add(it)
        } else {
            allowedResources.add(it)
        }
    }

    return if (allowedResources.isNotEmpty()) {
        allowedResources
    } else {
        emptyList()
    }
}

/**
 * Util to filter out events whose resources are blocked based on the resource tenant
 * resource-publish and resource-load events will need to be filtered
 * Both functions only return events whose resources are NOT blocked.
 * Events whose resources are blocked are logged and then tossed out.
 */
fun filterBlockedLoadEvents(
    event: InteropResourceLoadV1,
    tenantConfigService: TenantConfigurationService,
): InteropResourceLoadV1? {
    // check tenant config for blocked resources
    val isBlocked =
        tenantConfigService.getConfiguration(
            event.tenantId,
            // check if the resource type related to the event is in the blocked resources
        ).blockedResources?.split(",")?.contains(event.resourceType.toString()) != true

    return if (isBlocked) {
        event
    } else {
        null
    }
}

fun filterBlockedPublishedEvents(
    event: InteropResourcePublishV1,
    tenantConfigService: TenantConfigurationService,
): InteropResourcePublishV1? {
    // check tenant config for blocked resources
    val isBlocked =
        tenantConfigService.getConfiguration(
            event.tenantId,
            // check if the resource type related to the event is in the blocked resources
        ).blockedResources?.split(",")?.contains(event.resourceType.toString()) != true

    return if (isBlocked) {
        event
    } else {
        null
    }
}
