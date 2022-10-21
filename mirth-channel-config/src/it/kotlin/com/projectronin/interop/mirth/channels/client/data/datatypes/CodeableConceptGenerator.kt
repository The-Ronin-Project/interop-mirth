package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

class CodeableConceptGenerator : Generator<CodeableConcept>() {
    val coding: ListGenerator<Coding> = ListGenerator(0, NullGenerator())
    val text: Generator<String> = NullGenerator()

    override fun generateInternal(): CodeableConcept? =
        CodeableConcept(
            coding = coding.generate(),
            text = text.generate()
        )
}

fun codeableConcept(block: CodeableConceptGenerator.() -> Unit): CodeableConcept {
    val codeableConcept = CodeableConceptGenerator()
    codeableConcept.apply(block)
    return codeableConcept.generate()!!
}
