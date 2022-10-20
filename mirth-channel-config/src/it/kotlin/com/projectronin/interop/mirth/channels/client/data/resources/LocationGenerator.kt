package com.projectronin.interop.mirth.channels.client.data.resources

import com.github.javafaker.Faker
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.datatypes.IdentifierGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

class LocationGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator())
) : FhirTestResource<Location> {

    override fun toFhir(): Location =
        Location(
            id = id.generate(),
            identifier = identifier.generate(),
            name = Faker().university().name() + " Medical Center"
        )
}

fun location(block: LocationGenerator.() -> Unit): Location {
    val location = LocationGenerator()
    location.apply(block)
    return location.toFhir()
}
