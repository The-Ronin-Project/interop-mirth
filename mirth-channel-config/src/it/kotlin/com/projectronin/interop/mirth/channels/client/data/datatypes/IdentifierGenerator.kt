package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Period
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.RandomStringGenerator

class IdentifierGenerator : Generator<Identifier>() {
    val use: Generator<Code> = NullGenerator()
    val type: Generator<CodeableConcept> = CodeableConceptGenerator()
    val system: UriGenerator = UriGenerator()
    val value: Generator<String> = RandomStringGenerator(5, 15)
    val period: Generator<Period> = NullGenerator()
    val assigner: Generator<Reference> = NullGenerator()

    override fun generateInternal(): Identifier? =
        Identifier(
            use = use.generate(),
            type = type.generate(),
            system = system.generate(),
            value = value.generate()?.asFHIR(),
            period = period.generate(),
            assigner = assigner.generate()
        )
}

fun identifier(block: IdentifierGenerator.() -> Unit): Identifier {
    val identifier = IdentifierGenerator()
    identifier.apply(block)
    return identifier.generate()!!
}

fun externalIdentifier(block: IdentifierGenerator.() -> Unit): Identifier {
    val identifier = IdentifierGenerator()
    identifier.apply(block)
    identifier.type of codeableConcept {
        text of "External"
    }
    return identifier.generate()!!
}

fun internalIdentifier(block: IdentifierGenerator.() -> Unit): Identifier {
    val identifier = IdentifierGenerator()
    identifier.apply(block)
    identifier.type of codeableConcept {
        text of "Internal"
    }
    return identifier.generate()!!
}
