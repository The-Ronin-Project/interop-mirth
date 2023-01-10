package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Period
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

class ParticipantGenerator : Generator<Participant>() {
    val type: ListGenerator<CodeableConcept> = ListGenerator(0, CodeableConceptGenerator())
    val actor: Generator<Reference> = ReferenceGenerator()
    val required: CodeGenerator = CodeGenerator()
    val status: CodeGenerator = CodeGenerator()
    val period: Generator<Period> = NullGenerator()

    override fun generateInternal(): Participant =
        Participant(
            type = type.generate(),
            actor = actor.generate(),
            required = required.generate(),
            status = status.generate(),
            period = period.generate()
        )
}

fun participant(block: ParticipantGenerator.() -> Unit): Participant {
    val participant = ParticipantGenerator()
    participant.apply(block)
    return participant.generate()!!
}
