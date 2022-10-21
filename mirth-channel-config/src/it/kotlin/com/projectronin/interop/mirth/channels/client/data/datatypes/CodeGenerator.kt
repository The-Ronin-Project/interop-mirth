package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

class CodeGenerator : NullGenerator<Code>() {
    infix fun of(value: String) {
        of(Code(value))
    }
}
