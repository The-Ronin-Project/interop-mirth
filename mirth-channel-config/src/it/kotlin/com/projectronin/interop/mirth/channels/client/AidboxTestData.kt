package com.projectronin.interop.mirth.channels.client

import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.CodeableConcepts
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Resource

object AidboxTestData {
    val currentResources = mutableListOf<Resource<*>>()

    inline fun <reified T : Resource<T>> add(resource: Resource<T>): String {
        val insertedResource = AidboxClient.addResource<T>(resource)
        currentResources.add(insertedResource)
        return insertedResource.id!!.value!!
    }

    fun purge() {
        currentResources.forEach {
            AidboxClient.deleteResource(it.resourceType, it.id!!.value!!)
        }
        currentResources.clear()
    }
}

fun tenantIdentifier(tenantMnemonic: String): Identifier =
    Identifier(type = CodeableConcepts.RONIN_TENANT, system = CodeSystem.RONIN_TENANT.uri, value = tenantMnemonic.asFHIR())
