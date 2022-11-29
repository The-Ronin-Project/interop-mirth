package com.projectronin.interop.mirth.channels.client.data.resources

import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Participant
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Instant
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.mirth.channels.client.data.Generator
import com.projectronin.interop.mirth.channels.client.data.datatypes.CodeGenerator
import com.projectronin.interop.mirth.channels.client.data.datatypes.IdentifierGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.EmptyListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.InstantGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.ListGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.NullGenerator
import com.projectronin.interop.mirth.channels.client.data.primitives.RandomIntGenerator

data class AppointmentGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator()),
    val status: CodeGenerator = CodeGenerator(),
    val participant: ListGenerator<Participant> = EmptyListGenerator(),
    val minutesDuration: Generator<Int> = RandomIntGenerator(10, 120),
    val start: Generator<Instant> = InstantGenerator(),
    val end: Generator<Instant> = InstantGenerator()
) : FhirTestResource<Appointment> {
    override fun toFhir(): Appointment =
        Appointment(
            id = id.generate(),
            identifier = identifier.generate(),
            status = status.generate(),
            participant = participant.generate(),
            minutesDuration = minutesDuration.generate()?.asFHIR(),
            start = start.generate(),
            end = end.generate()
        )
}

fun appointment(block: AppointmentGenerator.() -> Unit): Appointment {
    val appointment = AppointmentGenerator()
    appointment.apply(block)
    return appointment.toFhir()
}
