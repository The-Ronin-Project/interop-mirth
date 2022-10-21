package com.projectronin.interop.mirth.channels.client.data.datatypes

import com.github.javafaker.Faker
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Period
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.primitives.FakerGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

class HumanNameGenerator : Generator<HumanName>() {
    private val fakerName = Faker().name()

    val use: CodeGenerator = CodeGenerator()
    val text: Generator<String> = NullGenerator()
    val family: Generator<String> = FakerGenerator(fakerName::lastName)
    val given: ListGenerator<String> = ListGenerator(1, FakerGenerator(fakerName::firstName))
    val prefix: ListGenerator<String> = ListGenerator(0, FakerGenerator(fakerName::prefix))
    val suffix: ListGenerator<String> = ListGenerator(0, FakerGenerator(fakerName::suffix))
    val period: Generator<Period> = NullGenerator()

    override fun generateInternal(): HumanName {
        return HumanName(
            use = use.generate(),
            text = text.generate(),
            family = family.generate(),
            given = given.generate(),
            prefix = prefix.generate(),
            suffix = suffix.generate(),
            period = period.generate()
        )
    }
}
fun name(block: HumanNameGenerator.() -> Unit): HumanName {
    val name = HumanNameGenerator()
    name.apply(block)
    return name.generate()!!
}
