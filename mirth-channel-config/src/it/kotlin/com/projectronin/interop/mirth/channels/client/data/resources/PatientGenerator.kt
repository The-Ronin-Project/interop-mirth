package com.projectronin.interop.mirth.channels.client.data.resources

import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.datatypes.CodeGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.HumanNameGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.IdentifierGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.DateGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator

data class PatientGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator()),
    val name: ListGenerator<HumanName> = ListGenerator(1, HumanNameGenerator()),
    val gender: CodeGenerator = CodeGenerator(),
    val birthDate: Generator<Date> = DateGenerator()
) : FhirTestResource<Patient> {
    override fun toFhir(): Patient =
        Patient(
            id = id.generate(),
            identifier = identifier.generate(),
            name = name.generate(),
            gender = gender.generate(),
            birthDate = birthDate.generate()
        )
}

fun patient(block: PatientGenerator.() -> Unit): Patient {
    val patient = PatientGenerator()
    patient.apply(block)
    return patient.toFhir()
}
