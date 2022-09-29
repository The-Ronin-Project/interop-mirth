package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.fhir.ronin.code.RoninCodeSystem
import com.projectronin.interop.fhir.ronin.code.RoninCodeableConcepts

object AidboxTestData {
    val currentResources = mutableListOf<Resource<*>>()

    inline fun <reified T : Resource<T>> add(vararg resource: Resource<T>) {
        resource.forEach {
            val insertedResource = AidboxClient.addResource(it)
            currentResources.add(insertedResource)
        }
    }

    fun purge() {
        currentResources.forEach {
            AidboxClient.deleteResource(it.resourceType, it.id!!.value)
        }
        currentResources.clear()
    }
}

fun tenantIdentifier(tenantMnemonic: String): Identifier =
    Identifier(type = RoninCodeableConcepts.TENANT, system = RoninCodeSystem.TENANT.uri, value = tenantMnemonic)
