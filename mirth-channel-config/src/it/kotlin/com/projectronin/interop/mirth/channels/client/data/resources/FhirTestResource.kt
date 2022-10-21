package com.projectronin.interop.mirth.channels.client.data.resources

import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Resource
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator

interface FhirTestResource<T : Resource<T>> {
    val id: Generator<Id>
    val identifier: ListGenerator<Identifier>

    fun toFhir(): T
}
