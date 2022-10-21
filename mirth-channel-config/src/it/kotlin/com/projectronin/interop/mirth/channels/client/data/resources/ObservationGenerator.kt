package com.projectronin.interop.mirth.channels.client.data.resources

import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.DateTime
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.datatypes.CodeGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.CodeableConceptGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.IdentifierGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.ReferenceGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.DateTimeGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

data class ObservationGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator()),
    val status: CodeGenerator = CodeGenerator(),
    val category: ListGenerator<CodeableConcept> = ListGenerator(0, CodeableConceptGenerator()),
    val code: Generator<CodeableConcept> = CodeableConceptGenerator(),
    val subject: Generator<Reference> = ReferenceGenerator(),
    val effective: Generator<DateTime> = DateTimeGenerator(),

) : FhirTestResource<Observation> {
    override fun toFhir(): Observation =
        Observation(
            id = id.generate(),
            identifier = identifier.generate(),
            category = category.generate(),
            code = code.generate(),
            subject = subject.generate(),
            status = status.generate(),
            effective = DynamicValue(type = DynamicValueType.DATE_TIME, value = effective.generate()!!)
        )
}

fun observation(block: ObservationGenerator.() -> Unit): Observation {
    val observation = ObservationGenerator()
    observation.apply(block)
    return observation.toFhir()
}
