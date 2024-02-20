package com.projectronin.interop.mirth.channel.util

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.mirth.service.TenantConfigurationService
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

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
    // if targeted resources is empty, call blocked resources and return
    val allowedResources =
        if (events.first().metadata.targetedResources?.isEmpty() == true) {
            filterBlockedLoadEvents(
                events,
                tenantConfigService,
            )
        } else {
            events.filter {
                it.metadata.targetedResources?.contains(it.resourceType.toString()) == true
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
    // if events is empty just return it
    if (events.isEmpty()) return events
    // if targeted resources is empty, call blocked resources and return
    val allowedResources =
        if (events.first().metadata.targetedResources?.isEmpty() == true) {
            filterBlockedPublishedEvents(
                events,
                tenantConfigService,
            )
        } else {
            events.filter {
                it.metadata.targetedResources?.contains(it.resourceType.toString()) == true
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
    events: List<InteropResourceLoadV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourceLoadV1> {
    if (events.isEmpty()) return events
    return events.filter { resource ->
        // check tenant config for blocked resources
        tenantConfigService.getConfiguration(
            resource.tenantId,
            // check if the resource type related to the event is in the blocked resources
        ).blockedResources?.split(",")?.contains(resource.resourceType.toString()) != true
    }
}

fun filterBlockedPublishedEvents(
    events: List<InteropResourcePublishV1>,
    tenantConfigService: TenantConfigurationService,
): List<InteropResourcePublishV1> {
    if (events.isEmpty()) return events
    return events.filter { resource ->
        // check tenant config for blocked resources
        tenantConfigService.getConfiguration(
            resource.tenantId,
            // check if the resource type related to the event is in the blocked resources
        ).blockedResources?.split(",")?.contains(resource.resourceType.toString()) != true
    }
}
