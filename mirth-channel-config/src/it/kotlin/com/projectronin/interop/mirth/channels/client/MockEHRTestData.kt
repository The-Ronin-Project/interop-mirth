package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.fhir.r4.resource.Resource

object MockEHRTestData {
    val currentResources = mutableMapOf<String, MutableSet<String>>()

    inline fun <reified T : Resource<T>> add(resource: Resource<T>): String {
        val insertedResourceId = MockEHRClient.addResource(resource)
        currentResources.computeIfAbsent(resource.resourceType) { mutableSetOf() }.add(insertedResourceId)
        return insertedResourceId
    }

    fun purge() {
        currentResources.forEach { (resourceType, ids) ->
            ids.forEach {
                MockEHRClient.deleteResource(resourceType, it)
            }
        }
        currentResources.clear()
    }
}
