package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

class ReferenceGenerator : Generator<Reference>() {
    val id: Generator<String> = NullGenerator()
    val type: UriGenerator = UriGenerator()
    val reference: Generator<String> = NullGenerator()
    val identifier: Generator<Identifier> = IdentifierGenerator()

    override fun generateInternal(): Reference? =
        Reference(
            id = id.generate()?.asFHIR(),
            type = type.generate(),
            reference = reference.generate()?.asFHIR(),
            identifier = identifier.generate()
        )
}

fun reference(type: String, id: String): Reference {
    val reference = ReferenceGenerator()
    reference.type of type
    reference.id of id
    reference.reference of "$type/$id"
    return reference.generate()!!
}
