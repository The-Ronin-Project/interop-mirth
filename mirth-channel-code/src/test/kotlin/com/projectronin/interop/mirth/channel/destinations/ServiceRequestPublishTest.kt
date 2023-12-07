package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.ServiceRequestService
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
import com.projectronin.interop.fhir.r4.resource.DiagnosticReport
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.EncounterLocation
import com.projectronin.interop.fhir.r4.resource.MedicationRequest
import com.projectronin.interop.fhir.r4.resource.MedicationStatement
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Procedure
import com.projectronin.interop.fhir.r4.resource.ServiceRequest
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.DiagnosticReportStatus
import com.projectronin.interop.fhir.r4.valueset.EncounterStatus
import com.projectronin.interop.fhir.r4.valueset.MedicationRequestIntent
import com.projectronin.interop.fhir.r4.valueset.MedicationRequestStatus
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

class ServiceRequestPublishTest {
    private val tenantId = "tenant"
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns tenantId
        }
    private val serviceRequestService = mockk<ServiceRequestService>()
    private val vendorFactory =
        mockk<VendorFactory> {
            every { serviceRequestService } returns this@ServiceRequestPublishTest.serviceRequestService
        }
    private val serviceRequestPublish = ServiceRequestPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))

    private val medicationRequest1 =
        MedicationRequest(
            id = Id("$tenantId-1234"),
            medication =
                DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("Medication/$tenantId-1234")),
                ),
            intent = MedicationRequestIntent.FILLER_ORDER.asCode(),
            status = MedicationRequestStatus.ACTIVE.asCode(),
            subject = Reference(reference = FHIRString("Patient/$tenantId-1234")),
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-1234")),
                ),
        )
    private val medicationRequest2 =
        MedicationRequest(
            id = Id("$tenantId-5678"),
            medication =
                DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("Medication/$tenantId-5678")),
                ),
            intent = MedicationRequestIntent.FILLER_ORDER.asCode(),
            status = MedicationRequestStatus.ACTIVE.asCode(),
            subject = Reference(reference = FHIRString("Patient/$tenantId-1234")),
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-5678")),
                ),
        )
    private val medicationRequest3 =
        MedicationRequest(
            id = Id("$tenantId-9012"),
            medication =
                DynamicValue(
                    DynamicValueType.REFERENCE,
                    Reference(reference = FHIRString("SomethingElse/$tenantId-9012")),
                ),
            intent = MedicationRequestIntent.FILLER_ORDER.asCode(),
            status = MedicationRequestStatus.ACTIVE.asCode(),
            subject = Reference(reference = FHIRString("Patient/$tenantId-1234")),
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-9012")),
                ),
        )

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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-1234")),
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-5678")),
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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-9012")),
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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-1234")),
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-5678")),
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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-9012")),
                ),
        )
    private val encounter3 =
        Encounter(
            id = Id("$tenantId-1234"),
            `class` = Coding(display = FHIRString("class")),
            status = EncounterStatus.FINISHED.asCode(),
        )

    private val diagnosticReport1 =
        DiagnosticReport(
            id = Id("$tenantId-1234"),
            status = DiagnosticReportStatus.APPENDED.asCode(),
            code = CodeableConcept(),
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-1234")),
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-5678")),
                ),
        )
    private val diagnosticReport2 =
        DiagnosticReport(
            id = Id("$tenantId-5678"),
            status = DiagnosticReportStatus.APPENDED.asCode(),
            code = CodeableConcept(),
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-9012")),
                ),
        )
    private val diagnosticReport3 =
        DiagnosticReport(
            id = Id("$tenantId-9012"),
            status = DiagnosticReportStatus.APPENDED.asCode(),
            code = CodeableConcept(),
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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-1234")),
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-5678")),
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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-9012")),
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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-1234")),
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-5678")),
                ),
        )
    private val observation2 =
        Observation(
            id = Id("$tenantId-5678"),
            status = ObservationStatus.REGISTERED.asCode(),
            code = CodeableConcept(),
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-9012")),
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
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-1234")),
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-5678")),
                ),
        )
    private val procedure2 =
        Procedure(
            id = Id("$tenantId-5678"),
            status = Code(value = "ProcedureCode"),
            code = CodeableConcept(),
            subject = Reference(),
            basedOn =
                listOf(
                    Reference(reference = FHIRString("ServiceRequest/$tenantId-9012")),
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
    fun `publish events create a PatientPublishServiceRequestRequest for patient publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Patient
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(patient1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.PatientPublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events create a MedicationRequestPublishServiceRequestRequest for medication request publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.MedicationRequest
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(medicationRequest1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.MedicationRequestPublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events create a AppointmentPublishServiceRequestRequest for appointment publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Appointment
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(appointment1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.AppointmentPublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events create a EncounterPublishServiceRequestRequest for encounter publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Encounter
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(encounter1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.EncounterPublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events create a DiagnosticReportPublishServiceRequestRequest for diagnostic report publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.DiagnosticReport
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(diagnosticReport1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.DiagnosticReportPublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events create a MedicationStatementPublishServiceRequestRequest for medication statement publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.MedicationStatement
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(medicationStatement1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.MedicationStatementPublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events create a ObservationPublishServiceRequestRequest for observation publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Observation
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(observation1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.ObservationPublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events create a ProcedurePublishServiceRequestRequest for procedure publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Procedure
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(procedure1)
                every { metadata } returns this@ServiceRequestPublishTest.metadata
            }
        val request = serviceRequestPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.ProcedurePublishServiceRequestRequest::class.java, request)
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Practitioner
            }
        val exception =
            assertThrows<IllegalStateException> {
                serviceRequestPublish.convertPublishEventsToRequest(
                    listOf(publishEvent),
                    vendorFactory,
                    tenant,
                )
            }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load service requests",
            exception.message,
        )
    }

    @Test
    fun `load events create a LoadServiceRequestRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = serviceRequestPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(ServiceRequestPublish.LoadServiceRequestRequest::class.java, request)
    }

    @Test
    fun `PatientPublishServiceRequestRequest supports loads resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every { serviceRequestService.getServiceRequestsForPatient(tenant, "1234") } returns
            listOf(
                serviceRequest1,
                serviceRequest2,
            )
        every { serviceRequestService.getServiceRequestsForPatient(tenant, "5678") } returns listOf(serviceRequest3)
        every { serviceRequestService.getServiceRequestsForPatient(tenant, "9012") } returns emptyList()

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Patient,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(patient1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Patient,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(patient2),
                metadata = metadata,
            )

        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.Patient,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(patient3),
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
            ServiceRequestPublish.PatientPublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1, serviceRequest2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012", Pair(startDate, endDate))
        assertEquals(emptyList<ServiceRequest>(), resourcesByKeys[key3])
    }

    @Test
    fun `MedicationRequestPublishServiceRequestRequest supports load resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            serviceRequestService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to serviceRequest1, "5678" to serviceRequest2, "9012" to serviceRequest3)

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.MedicationRequest,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationRequest1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.MedicationRequest,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationRequest2),
                metadata = metadata,
            )
        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.MedicationRequest,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(medicationRequest3),
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
            ServiceRequestPublish.MedicationRequestPublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-9012")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key3])
    }

    @Test
    fun `AppointmentPublishServiceRequestRequest supports loads resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        every {
            serviceRequestService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to serviceRequest1, "5678" to serviceRequest2, "9012" to serviceRequest3)

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
            ServiceRequestPublish.AppointmentPublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-9012")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key3])
    }

    @Test
    fun `EncounterPublishServiceRequestRequest supports loads resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        every {
            serviceRequestService.getByIDs(
                tenant,
                listOf("1234", "5678", "9012"),
            )
        } returns mapOf("1234" to serviceRequest1, "5678" to serviceRequest2, "9012" to serviceRequest3)

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
            ServiceRequestPublish.EncounterPublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-9012")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key3])
    }

    @Test
    fun `DiagnosticReportPublishServiceRequestRequest supports loads resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        every {
            serviceRequestService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to serviceRequest1, "5678" to serviceRequest2, "9012" to serviceRequest3)

        val event1 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.DiagnosticReport,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(diagnosticReport1),
                metadata = metadata,
            )
        val event2 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.DiagnosticReport,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(diagnosticReport2),
                metadata = metadata,
            )
        val event3 =
            InteropResourcePublishV1(
                tenantId = tenantId,
                resourceType = ResourceType.DiagnosticReport,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(diagnosticReport3),
                metadata = metadata,
            )
        val request =
            ServiceRequestPublish.DiagnosticReportPublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-9012")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key3])
    }

    @Test
    fun `MedicationStatementPublishServiceRequestRequest supports load resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            serviceRequestService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to serviceRequest1, "5678" to serviceRequest2, "9012" to serviceRequest3)

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
            ServiceRequestPublish.MedicationStatementPublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-9012")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key3])
    }

    @Test
    fun `ObservationPublishServiceRequestRequest supports load resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            serviceRequestService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to serviceRequest1, "5678" to serviceRequest2, "9012" to serviceRequest3)

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
            ServiceRequestPublish.ObservationPublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-9012")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key3])
    }

    @Test
    fun `ProcedurePublishServiceRequestRequest supports load resources`() {
        val serviceRequest1 = mockk<ServiceRequest>()
        val serviceRequest2 = mockk<ServiceRequest>()
        val serviceRequest3 = mockk<ServiceRequest>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            serviceRequestService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to serviceRequest1, "5678" to serviceRequest2, "9012" to serviceRequest3)

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
            ServiceRequestPublish.ProcedurePublishServiceRequestRequest(
                listOf(event1, event2, event3),
                serviceRequestService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-1234")
        assertEquals(listOf(serviceRequest1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-5678")
        assertEquals(listOf(serviceRequest2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.ServiceRequest, tenant, "$tenantId-9012")
        assertEquals(listOf(serviceRequest3), resourcesByKeys[key3])
    }
}
