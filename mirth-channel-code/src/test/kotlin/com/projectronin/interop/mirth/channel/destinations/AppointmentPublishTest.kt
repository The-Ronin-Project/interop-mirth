package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.AppointmentService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class AppointmentPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val appointmentService = mockk<AppointmentService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { appointmentService } returns this@AppointmentPublishTest.appointmentService
    }
    private val appointmentPublish = AppointmentPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))
    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
    }

    @Test
    fun `publish events create a PatientPublishAppointmentRequest`() {
        val publishEvent = mockk<InteropResourcePublishV1>()
        val request = appointmentPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(AppointmentPublish.PatientPublishAppointmentRequest::class.java, request)
    }

    @Test
    fun `load events create a LoadAppointmentRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = appointmentPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(AppointmentPublish.LoadAppointmentRequest::class.java, request)
    }

    @Test
    fun `PatientPublishAppointmentRequest supports loads resources`() {
        val appointment1 = mockk<Appointment>()
        val appointment2 = mockk<Appointment>()
        val appointment3 = mockk<Appointment>()
        every { appointmentService.findPatientAppointments(tenant, "1234", any(), any()) } returns listOf(
            appointment1,
            appointment2
        )
        every { appointmentService.findPatientAppointments(tenant, "5678", any(), any()) } returns listOf(appointment3)
        every { appointmentService.findPatientAppointments(tenant, "9012", any(), any()) } returns emptyList()

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient3),
            metadata = metadata
        )
        val request =
            AppointmentPublish.PatientPublishAppointmentRequest(
                listOf(event1, event2, event3),
                appointmentService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(appointment1, appointment2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(appointment3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012")
        assertEquals(emptyList<Appointment>(), resourcesByKeys[key3])
    }
}
