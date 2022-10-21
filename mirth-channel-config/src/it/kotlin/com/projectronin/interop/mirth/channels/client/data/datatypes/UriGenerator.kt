package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.mirth.channels.client.data.primitives.RandomGenerator

class UriGenerator : RandomGenerator<Uri>() {
    override fun generateInternal(): Uri {
        return Uri(randomString(10, 50))
    }

    infix fun of(value: String) {
        of(Uri(value))
    }
}
