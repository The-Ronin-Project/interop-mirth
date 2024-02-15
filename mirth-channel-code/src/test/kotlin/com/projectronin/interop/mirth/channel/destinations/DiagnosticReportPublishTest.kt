package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.DiagnosticReportService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.DiagnosticReport
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class DiagnosticReportPublishTest {
    private val tenantId = "tenant"
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns tenantId
        }
    private val diagnosticReportService = mockk<DiagnosticReportService>()
    private val vendorFactory =
        mockk<VendorFactory> {
            every { diagnosticReportService } returns this@DiagnosticReportPublishTest.diagnosticReportService
        }
    private val diagnosticReportPublish = DiagnosticReportPublish(mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-12345678"))
    private val patient2 = Patient(id = Id("$tenantId-89101112"))
    private val patient3 = Patient(id = Id("$tenantId-87654321"))
    private val metadata =
        mockk<Metadata>(relaxed = true) {
            every { runId } returns "run"
            every { backfillRequest } returns null
        }

    @Test
    fun `publish events creates a PatientPublishDiagnosticReportRequest`() {
        val publishEvent = mockk<InteropResourcePublishV1>()
        val request =
            diagnosticReportPublish.convertPublishEventsToRequest(
                listOf(publishEvent),
                vendorFactory,
                tenant,
            )
        assertInstanceOf(DiagnosticReportPublish.PatientPublishDiagnosticReportRequest::class.java, request)
    }

    @Test
    fun `load events creates a LoadDiagnosticReportRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request =
            diagnosticReportPublish.convertLoadEventsToRequest(
                listOf(loadEvent),
                vendorFactory,
                tenant,
            )
        assertInstanceOf(DiagnosticReportPublish.LoadDiagnosticReportRequest::class.java, request)
    }

    @Test
    fun `PatientPublishDiagnosticReportRequest supports loads resources`() {
        val diagnosticReport1 = mockk<DiagnosticReport>()
        val diagnosticReport2 = mockk<DiagnosticReport>()
        val diagnosticReport3 = mockk<DiagnosticReport>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every {
            diagnosticReportService.getDiagnosticReportByPatient(
                tenant,
                "12345678",
                LocalDate.now().minusMonths(2),
                LocalDate.now(),
            )
        } returns listOf(diagnosticReport1, diagnosticReport2)
        every {
            diagnosticReportService.getDiagnosticReportByPatient(
                tenant,
                "89101112",
                LocalDate.now().minusMonths(2),
                LocalDate.now(),
            )
        } returns listOf(diagnosticReport3)
        every {
            diagnosticReportService.getDiagnosticReportByPatient(
                tenant,
                "87654321",
                startDate.toLocalDate(),
                endDate.toLocalDate(),
            )
        } returns emptyList()

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
            DiagnosticReportPublish.PatientPublishDiagnosticReportRequest(
                listOf(event1, event2, event3),
                diagnosticReportService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-12345678")
        assertEquals(listOf(diagnosticReport1, diagnosticReport2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-89101112")
        assertEquals(listOf(diagnosticReport3), resourcesByKeys[key2])

        val key3 =
            ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-87654321", Pair(startDate, endDate))
        assertEquals(emptyList<DiagnosticReport>(), resourcesByKeys[key3])
    }
}
