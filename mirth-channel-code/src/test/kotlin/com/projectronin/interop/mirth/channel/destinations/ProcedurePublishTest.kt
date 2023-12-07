package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.ProcedureService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.CodeableConcept
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.DynamicValue
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.EncounterLocation
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.fhir.r4.resource.Procedure
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.EncounterStatus
import com.projectronin.interop.fhir.r4.valueset.ObservationStatus
import com.projectronin.interop.fhir.r4.valueset.ParticipationStatus
import com.projectronin.interop.fhir.util.asCode
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime

class ProcedurePublishTest {
    private val tenantId = "tenant"
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns tenantId
        }
    private val procedureService = mockk<ProcedureService>()
    private val vendorFactory =
        mockk<VendorFactory> {
            every { procedureService } returns this@ProcedurePublishTest.procedureService
        }
    private val procedurePublish = ProcedurePublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val appointment1 =
        Appointment(
            id = Id("$tenantId-1234"),
            status = AppointmentStatus.BOOKED.asCode(),
            participant =
                listOf(
                    Participant(
                        actor = Reference(reference = FHIRString("Location/$tenantId-1234")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                    Participant(
                        actor = Reference(reference = FHIRString("Location/$tenantId-5678")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                ),
            reasonReference =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-1234")),
                    Reference(reference = FHIRString("Procedure/$tenantId-5678")),
                ),
        )
    private val appointment2 =
        Appointment(
            id = Id("$tenantId-5678"),
            status = AppointmentStatus.BOOKED.asCode(),
            participant =
                listOf(
                    Participant(
                        actor = Reference(reference = FHIRString("Location/$tenantId-9012")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                ),
            reasonReference =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-9012")),
                ),
        )
    private val appointment3 =
        Appointment(
            id = Id("$tenantId-9012"),
            status = AppointmentStatus.BOOKED.asCode(),
            participant =
                listOf(
                    Participant(
                        actor = Reference(reference = FHIRString("Practitioner/$tenantId-3456")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                ),
        )

    private val encounter1 =
        Encounter(
            id = Id("$tenantId-1234"),
            `class` = Coding(display = FHIRString("class")),
            status = EncounterStatus.FINISHED.asCode(),
            location =
                listOf(
                    EncounterLocation(location = Reference(reference = FHIRString("Location/$tenantId-1234"))),
                    EncounterLocation(location = Reference(reference = FHIRString("Location/$tenantId-5678"))),
                ),
            reasonReference =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-1234")),
                    Reference(reference = FHIRString("Procedure/$tenantId-5678")),
                ),
        )
    private val encounter2 =
        Encounter(
            id = Id("$tenantId-1234"),
            `class` = Coding(display = FHIRString("class")),
            status = EncounterStatus.FINISHED.asCode(),
            location =
                listOf(
                    EncounterLocation(location = Reference(reference = FHIRString("Location/$tenantId-9012"))),
                ),
            reasonReference =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-9012")),
                ),
        )
    private val encounter3 =
        Encounter(
            id = Id("$tenantId-1234"),
            `class` = Coding(display = FHIRString("class")),
            status = EncounterStatus.FINISHED.asCode(),
        )

    private val medicationStatement1 =
        MedicationStatement(
            id = Id("$tenantId-1234"),
            medication =
                DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("Medication/$tenantId-1234")),
                ),
            status = com.projectronin.interop.fhir.r4.valueset.MedicationStatementStatus.ACTIVE.asCode(),
            subject = Reference(reference = FHIRString("Patient/$tenantId-1234")),
            partOf =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-1234")),
                    Reference(reference = FHIRString("Procedure/$tenantId-5678")),
                ),
        )
    private val medicationStatement2 =
        MedicationStatement(
            id = Id("$tenantId-5678"),
            medication =
                DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("Medication/$tenantId-5678")),
                ),
            status = com.projectronin.interop.fhir.r4.valueset.MedicationStatementStatus.ACTIVE.asCode(),
            subject = Reference(reference = FHIRString("Patient/$tenantId-1234")),
            partOf =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-9012")),
                ),
        )
    private val medicationStatement3 =
        MedicationStatement(
            id = Id("$tenantId-9012"),
            medication =
                DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("SomethingElse/$tenantId-9012")),
                ),
            status = com.projectronin.interop.fhir.r4.valueset.MedicationStatementStatus.ACTIVE.asCode(),
            subject = Reference(reference = FHIRString("Patient/$tenantId-1234")),
        )

    private val observation1 =
        Observation(
            id = Id("$tenantId-1234"),
            status = ObservationStatus.REGISTERED.asCode(),
            code = CodeableConcept(),
            partOf =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-1234")),
                    Reference(reference = FHIRString("Procedure/$tenantId-5678")),
                ),
        )
    private val observation2 =
        Observation(
            id = Id("$tenantId-5678"),
            status = ObservationStatus.REGISTERED.asCode(),
            code = CodeableConcept(),
            partOf =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-9012")),
                ),
        )
    private val observation3 =
        Observation(
            id = Id("$tenantId-9012"),
            status = ObservationStatus.REGISTERED.asCode(),
            code = CodeableConcept(),
        )

    private val procedure1 =
        Procedure(
            id = Id("$tenantId-1234"),
            status = Code(value = "ProcedureCode"),
            code = CodeableConcept(),
            subject = Reference(),
            partOf =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-1234")),
                    Reference(reference = FHIRString("Procedure/$tenantId-5678")),
                ),
        )
    private val procedure2 =
        Procedure(
            id = Id("$tenantId-5678"),
            status = Code(value = "ProcedureCode"),
            code = CodeableConcept(),
            subject = Reference(),
            partOf =
                listOf(
                    Reference(reference = FHIRString("Procedure/$tenantId-9012")),
                ),
        )
    private val procedure3 =
        Procedure(
            id = Id("$tenantId-9012"),
            status = Code(value = "ProcedureCode"),
            code = CodeableConcept(),
            subject = Reference(),
        )

    private val metadata =
        mockk<Metadata>(relaxed = true) {
            every { runId } returns "run"
            every { backfillRequest } returns null
        }

    @Test
    fun `publish events create a AppointmentPublishProcedureRequest for appointment publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Appointment
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(appointment1)
                every { metadata } returns this@ProcedurePublishTest.metadata
            }
        val request = procedurePublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ProcedurePublish.AppointmentPublishProcedureRequest::class.java, request)
    }

    @Test
    fun `publish events create a EncounterPublishProcedureRequest for encounter publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Encounter
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(encounter1)
                every { metadata } returns this@ProcedurePublishTest.metadata
            }
        val request = procedurePublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ProcedurePublish.EncounterPublishProcedureRequest::class.java, request)
    }

    @Test
    fun `publish events create a MedicationStatementPublishProcedureRequest for medication statement publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.MedicationStatement
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(medicationStatement1)
                every { metadata } returns this@ProcedurePublishTest.metadata
            }
        val request = procedurePublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ProcedurePublish.MedicationStatementPublishProcedureRequest::class.java, request)
    }

    @Test
    fun `publish events create a ObservationPublishProcedureRequest for observation publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Observation
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(observation1)
                every { metadata } returns this@ProcedurePublishTest.metadata
            }
        val request = procedurePublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ProcedurePublish.ObservationPublishProcedureRequest::class.java, request)
    }

    @Test
    fun `publish events create a ProcedurePublishProcedureRequest for procedure publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Procedure
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(procedure1)
                every { metadata } returns this@ProcedurePublishTest.metadata
            }
        val request = procedurePublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ProcedurePublish.ProcedurePublishProcedureRequest::class.java, request)
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Practitioner
            }
        val exception =
            assertThrows<IllegalStateException> {
                procedurePublish.convertPublishEventsToRequest(
                    listOf(publishEvent),
                    vendorFactory,
                    tenant,
                )
            }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load procedures",
            exception.message,
        )
    }

    @Test
    fun `load events create a LoadProcedureRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = procedurePublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(ProcedurePublish.LoadProcedureRequest::class.java, request)
    }

    @Test
    fun `AppointmentPublishProcedureRequest supports loads resources`() {
        val procedure1 = mockk<Procedure>()
        val procedure2 = mockk<Procedure>()
        val procedure3 = mockk<Procedure>()
        every {
            procedureService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to procedure1, "5678" to procedure2, "9012" to procedure3)

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Appointment,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(appointment1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Appointment,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(appointment2),
                metadata = metadata,
            )
        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Appointment,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(appointment3),
                metadata = metadata,
            )
        val request =
            ProcedurePublish.AppointmentPublishProcedureRequest(
                listOf(event1, event2, event3),
                procedureService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-1234")
        assertEquals(listOf(procedure1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-5678")
        assertEquals(listOf(procedure2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-9012")
        assertEquals(listOf(procedure3), resourcesByKeys[key3])
    }

    @Test
    fun `EncounterPublishProcedureRequest supports loads resources`() {
        val procedure1 = mockk<Procedure>()
        val procedure2 = mockk<Procedure>()
        val procedure3 = mockk<Procedure>()
        every {
            procedureService.getByIDs(
                tenant,
                listOf("1234", "5678", "9012"),
            )
        } returns mapOf("1234" to procedure1, "5678" to procedure2, "9012" to procedure3)

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Encounter,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(encounter1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Encounter,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(encounter2),
                metadata = metadata,
            )
        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Encounter,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(encounter3),
                metadata = metadata,
            )
        val request =
            ProcedurePublish.EncounterPublishProcedureRequest(
                listOf(event1, event2, event3),
                procedureService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-1234")
        assertEquals(listOf(procedure1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-5678")
        assertEquals(listOf(procedure2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-9012")
        assertEquals(listOf(procedure3), resourcesByKeys[key3])
    }

    @Test
    fun `MedicationStatementPublishServiceRequestRequest supports load resources`() {
        val procedure1 = mockk<Procedure>()
        val procedure2 = mockk<Procedure>()
        val procedure3 = mockk<Procedure>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            procedureService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to procedure1, "5678" to procedure2, "9012" to procedure3)

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.MedicationStatement,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationStatement1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.MedicationStatement,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationStatement2),
                metadata = metadata,
            )
        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.MedicationStatement,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationStatement3),
                metadata =
                    Metadata(
                        runId = "run",
                        runDateTime = OffsetDateTime.now(),
                        upstreamReferences = null,
                        backfillRequest =
                            Metadata.BackfillRequest(
                                backfillId = "123",
                                backfillStartDate = startDate,
                                backfillEndDate = endDate,
                            ),
                    ),
            )
        val request =
            ProcedurePublish.MedicationStatementPublishProcedureRequest(
                listOf(event1, event2, event3),
                procedureService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-1234")
        assertEquals(listOf(procedure1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-5678")
        assertEquals(listOf(procedure2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-9012")
        assertEquals(listOf(procedure3), resourcesByKeys[key3])
    }

    @Test
    fun `ObservationPublishProcedureRequest supports load resources`() {
        val procedure1 = mockk<Procedure>()
        val procedure2 = mockk<Procedure>()
        val procedure3 = mockk<Procedure>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            procedureService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to procedure1, "5678" to procedure2, "9012" to procedure3)

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Observation,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(observation1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Observation,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(observation2),
                metadata = metadata,
            )
        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Observation,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(observation3),
                metadata =
                    Metadata(
                        runId = "run",
                        runDateTime = OffsetDateTime.now(),
                        upstreamReferences = null,
                        backfillRequest =
                            Metadata.BackfillRequest(
                                backfillId = "123",
                                backfillStartDate = startDate,
                                backfillEndDate = endDate,
                            ),
                    ),
            )
        val request =
            ProcedurePublish.ObservationPublishProcedureRequest(
                listOf(event1, event2, event3),
                procedureService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-1234")
        assertEquals(listOf(procedure1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-5678")
        assertEquals(listOf(procedure2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-9012")
        assertEquals(listOf(procedure3), resourcesByKeys[key3])
    }

    @Test
    fun `ProcedurePublishProcedureRequest supports load resources`() {
        val mockProcedure1 = mockk<Procedure>()
        val mockProcedure2 = mockk<Procedure>()
        val mockProcedure3 = mockk<Procedure>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            procedureService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to mockProcedure1, "5678" to mockProcedure2, "9012" to mockProcedure3)

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Procedure,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(procedure1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Procedure,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(procedure2),
                metadata = metadata,
            )
        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Procedure,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(procedure3),
                metadata =
                    Metadata(
                        runId = "run",
                        runDateTime = OffsetDateTime.now(),
                        upstreamReferences = null,
                        backfillRequest =
                            Metadata.BackfillRequest(
                                backfillId = "123",
                                backfillStartDate = startDate,
                                backfillEndDate = endDate,
                            ),
                    ),
            )
        val request =
            ProcedurePublish.ProcedurePublishProcedureRequest(
                listOf(event1, event2, event3),
                procedureService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-1234")
        assertEquals(listOf(mockProcedure1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-5678")
        assertEquals(listOf(mockProcedure2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Procedure, tenant, "$tenantId-9012")
        assertEquals(listOf(mockProcedure3), resourcesByKeys[key3])
    }
}
