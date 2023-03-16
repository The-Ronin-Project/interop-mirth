package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.fhir.r4.resource.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.opentest4j.TestAbortedException

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

    fun validateAll() {
        repeat(5) {
            var goodToGo = true
            currentResources.keys.forEach { resourceType ->
                val resources = MockEHRClient.getAllResources(resourceType).get("total").asInt()
                if (resources != currentResources[resourceType]!!.size) {
                    goodToGo = false
                }
            }
            if (goodToGo) return
            runBlocking { delay(2000) }
        }
        throw TestAbortedException("MockEHR being slow")
    }
}
