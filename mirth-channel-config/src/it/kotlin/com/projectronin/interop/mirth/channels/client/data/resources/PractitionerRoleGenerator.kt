package com.projectronin.interop.mirth.channels.client.data.resources

import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.PractitionerRole
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.datatypes.IdentifierGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.ReferenceGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

data class PractitionerRoleGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator()),
    val practitioner: Generator<Reference> = ReferenceGenerator(),
    val location: ListGenerator<Reference> = ListGenerator(0, ReferenceGenerator())
) : FhirTestResource<PractitionerRole> {
    override fun toFhir(): PractitionerRole = PractitionerRole(
        id = id.generate(),
        identifier = identifier.generate(),
        practitioner = practitioner.generate(),
        location = location.generate()
    )
}

fun practitionerRole(block: PractitionerRoleGenerator.() -> Unit): PractitionerRole {
    val practitionerRole = PractitionerRoleGenerator()
    practitionerRole.apply(block)
    return practitionerRole.toFhir()
}
