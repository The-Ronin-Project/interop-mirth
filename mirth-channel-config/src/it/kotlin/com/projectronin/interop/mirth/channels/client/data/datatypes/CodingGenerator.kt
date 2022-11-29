package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

class CodingGenerator : Generator<Coding>() {
    val system: UriGenerator = UriGenerator()
    val version: Generator<String> = NullGenerator()
    val code: CodeGenerator = CodeGenerator()
    val display: Generator<String> = NullGenerator()
    val userSelected: Generator<Boolean> = NullGenerator()

    override fun generateInternal(): Coding? =
        Coding(
            system = system.generate(),
            version = version.generate()?.asFHIR(),
            code = code.generate(),
            display = display.generate()?.asFHIR(),
            userSelected = userSelected.generate()?.asFHIR()
        )
}

fun coding(block: CodingGenerator.() -> Unit): Coding {
    val coding = CodingGenerator()
    coding.apply(block)
    return coding.generate()!!
}
