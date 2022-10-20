package com.projectronin.interop.mirth.channels.client.data.resources

import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.datatypes.CodeableConceptGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.IdentifierGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.ReferenceGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

data class ConditionGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator()),
    val clinicalStatus: Generator<CodeableConcept> = CodeableConceptGenerator(),
    val category: ListGenerator<CodeableConcept> = ListGenerator(0, CodeableConceptGenerator()),
    val code: Generator<CodeableConcept> = CodeableConceptGenerator(),
    val subject: Generator<Reference> = ReferenceGenerator()
) : FhirTestResource<Condition> {
    override fun toFhir(): Condition =
        Condition(
            id = id.generate(),
            identifier = identifier.generate(),
            clinicalStatus = clinicalStatus.generate(),
            category = category.generate(),
            code = code.generate(),
            subject = subject.generate()
        )
}

fun condition(block: ConditionGenerator.() -> Unit): Condition {
    val condition = ConditionGenerator()
    condition.apply(block)
    return condition.toFhir()
}
