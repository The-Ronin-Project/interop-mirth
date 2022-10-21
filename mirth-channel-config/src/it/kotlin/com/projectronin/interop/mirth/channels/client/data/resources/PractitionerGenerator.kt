package com.projectronin.interop.mirth.channels.client.data.resources

import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.datatypes.HumanNameGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.IdentifierGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

data class PractitionerGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator()),
    val name: ListGenerator<HumanName> = ListGenerator(1, HumanNameGenerator())
) : FhirTestResource<Practitioner> {
    override fun toFhir(): Practitioner =
        Practitioner(
            id = id.generate(),
            identifier = identifier.generate(),
            name = name.generate()
        )
}

fun practitioner(block: PractitionerGenerator.() -> Unit): Practitioner {
    val practitioner = PractitionerGenerator()
    practitioner.apply(block)
    return practitioner.toFhir()
}
