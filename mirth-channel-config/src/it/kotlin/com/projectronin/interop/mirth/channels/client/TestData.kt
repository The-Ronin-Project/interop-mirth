package com.projectronin.interop.mirth.channels.client

import com.github.javafaker.Faker
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.HumanName
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Participant
import com.projectronin.interop.fhir.r4.datatype.Period
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Instant
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.resource.Resource
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random
import kotlin.reflect.KFunction

interface FhirTestResource<T : Resource<T>> {
    val id: Generator<Id>
    val identifier: ListGenerator<Identifier>

    fun toFhir(): T
}

data class PractitionerGenerator(
    override val id: Generator<Id> = NullGenerator(),
    override val identifier: ListGenerator<Identifier> = ListGenerator(0, IdentifierGenerator()),
    val name: ListGenerator<HumanName> = ListGenerator(1, HumanNameGenerator())
) : FhirTestResource<Practitioner> {
    override fun toFhir(): Practitioner =
        Practitioner(
            id = id.generate(),
            identifier = identifier.generate(),
            name = name.generate()
        )
}

fun practitioner(block: PractitionerGenerator.() -> Unit): Practitioner {
    val practitioner = PractitionerGenerator()
    practitioner.apply(block)
    return practitioner.toFhir()
}

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
            minutesDuration = minutesDuration.generate(),
            start = start.generate(),
            end = end.generate()
        )
}

fun appointment(block: AppointmentGenerator.() -> Unit): Appointment {
    val appointment = AppointmentGenerator()
    appointment.apply(block)
    return appointment.toFhir()
}

abstract class Generator<T> {
    private var staticValue: T? = null

    protected abstract fun generateInternal(): T?

    fun generate(): T? = staticValue ?: generateInternal()

    infix fun of(value: T) {
        staticValue = value
    }
}

open class NullGenerator<T> : Generator<T>() {
    override fun generateInternal(): T? = null
}

open class ListGenerator<T>(private var count: Int, private val baseGenerator: Generator<T>) {
    private var staticValue: List<T>? = null
    private var additionalValues: MutableList<T> = mutableListOf()

    fun generate(): List<T> {
        return staticValue ?: ((1..count).mapNotNull { baseGenerator.generate() } + additionalValues)
    }

    infix fun of(value: List<T>) {
        staticValue = value
    }

    infix fun generate(count: Int): ListGenerator<T> {
        this.count = count
        return this
    }

    infix fun plus(value: T): ListGenerator<T> {
        additionalValues.add(value)
        return this
    }

    fun copy(): ListGenerator<T> {
        val new = ListGenerator(count, baseGenerator)
        new.staticValue = staticValue
        new.additionalValues = additionalValues
        return new
    }
}

class EmptyListGenerator<T> : ListGenerator<T>(0, NullGenerator())

internal val faker = Faker()

class HumanNameGenerator : Generator<HumanName>() {
    private val fakerName = faker.name()

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

abstract class RandomGenerator<T> : Generator<T>() {
    private val random = Random.Default

    protected fun randomString(minimumLength: Int, maximumLength: Int): String {
        val length = random.nextInt(minimumLength, maximumLength)
        val word = (1..length).map { 'a' + random.nextInt(0, 26) }.joinToString("")
        return word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    protected fun randomInt(minimum: Int, maximum: Int): Int {
        return random.nextInt(minimum, maximum)
    }
}

open class RandomStringGenerator(private val minimumLength: Int = 1, private val maximumLength: Int = 50) :
    RandomGenerator<String>() {
    override fun generateInternal(): String {
        return randomString(minimumLength, maximumLength)
    }
}

open class RandomIntGenerator(private val minimum: Int = 1, private val maximum: Int = 50) :
    RandomGenerator<Int>() {
    override fun generateInternal(): Int {
        return randomInt(minimum, maximum)
    }
}

class FakerGenerator(private val function: KFunction<String>) : Generator<String>() {
    override fun generateInternal(): String? = function.call()
}

fun name(block: HumanNameGenerator.() -> Unit): HumanName {
    val name = HumanNameGenerator()
    name.apply(block)
    return name.generate()!!
}

class UriGenerator : RandomGenerator<Uri>() {
    override fun generateInternal(): Uri {
        return Uri(randomString(10, 50))
    }

    infix fun of(value: String) {
        of(Uri(value))
    }
}

class CodeGenerator : NullGenerator<Code>() {
    infix fun of(value: String) {
        of(Code(value))
    }
}

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
            value = value.generate(),
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

class CodingGenerator : Generator<Coding>() {
    val system: UriGenerator = UriGenerator()
    val version: Generator<String> = NullGenerator()
    val code: CodeGenerator = CodeGenerator()
    val display: Generator<String> = NullGenerator()
    val userSelected: Generator<Boolean> = NullGenerator()

    override fun generateInternal(): Coding? =
        Coding(
            system = system.generate(),
            version = version.generate(),
            code = code.generate(),
            display = display.generate(),
            userSelected = userSelected.generate()
        )
}

fun coding(block: CodingGenerator.() -> Unit): Coding {
    val coding = CodingGenerator()
    coding.apply(block)
    return coding.generate()!!
}

class ParticipantGenerator : Generator<Participant>() {
    val type: ListGenerator<CodeableConcept> = ListGenerator(0, CodeableConceptGenerator())
    val actor: Generator<Reference> = ReferenceGenerator()
    val required: CodeGenerator = CodeGenerator()
    val status: CodeGenerator = CodeGenerator()
    val period: Generator<Period> = NullGenerator()

    override fun generateInternal(): Participant? =
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

class ReferenceGenerator : Generator<Reference>() {
    val id: Generator<String> = NullGenerator()
    val type: UriGenerator = UriGenerator()
    val reference: Generator<String> = NullGenerator()
    val identifier: Generator<Identifier> = IdentifierGenerator()

    override fun generateInternal(): Reference? =
        Reference(
            id = id.generate(),
            type = type.generate(),
            reference = reference.generate(),
            identifier = identifier.generate()
        )
}

fun reference(type: String, id: String): Reference {
    val reference = ReferenceGenerator()
    reference.type of type
    reference.id of id
    reference.reference of "$type/$id"
    return reference.generate()!!
}

class DateGenerator : RandomGenerator<Date>() {
    val year: Generator<Int> = RandomIntGenerator(1920, 2015)
    val month: Generator<Int> = RandomIntGenerator(1, 12)
    val day: Generator<Int> = RandomIntGenerator(1, 28) // To prevent invalid dates

    override fun generateInternal(): Date =
        Date("%d-%02d-%02d".format(year.generate(), month.generate(), day.generate()))
}

fun date(block: DateGenerator.() -> Unit): Date {
    val date = DateGenerator()
    date.apply(block)
    return date.generate()!!
}

class InstantGenerator : RandomGenerator<Instant>() {
    val year: Generator<Int> = RandomIntGenerator(1920, 2015)
    val month: Generator<Int> = RandomIntGenerator(1, 12)
    val day: Generator<Int> = RandomIntGenerator(1, 28) // To prevent invalid dates
    val hour: Generator<Int> = RandomIntGenerator(0, 23)
    val minute: Generator<Int> = RandomIntGenerator(0, 59)
    val second: Generator<Int> = RandomIntGenerator(0, 59)

    override fun generateInternal(): Instant =
        Instant(
            "%d-%02d-%02dT%02d:%02d:%02dZ".format(
                year.generate(),
                month.generate(),
                day.generate(),
                hour.generate(),
                minute.generate(),
                second.generate()
            )
        )
}

fun instant(block: InstantGenerator.() -> Unit): Instant {
    val instant = InstantGenerator()
    instant.apply(block)
    return instant.generate()!!
}

fun Int.daysAgo(): Instant {
    val adjustedNow = LocalDate.now().minusDays(this.toLong())
    return instant {
        year of adjustedNow.year
        month of adjustedNow.month.value
        day of adjustedNow.dayOfMonth
    }
}

fun Int.daysFromNow(): Instant {
    val adjustedNow = LocalDate.now().plusDays(this.toLong())
    return instant {
        year of adjustedNow.year
        month of adjustedNow.month.value
        day of adjustedNow.dayOfMonth
    }
}
