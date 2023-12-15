package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.PractitionerService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.ParticipationStatus
import com.projectronin.interop.fhir.util.asCode
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class PractitionerPublishTest {
    private val tenantId = "tenant"
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns tenantId
        }
    private val practitionerService = mockk<PractitionerService>()
    private val vendorFactory =
        mockk<VendorFactory> {
            every { practitionerService } returns this@PractitionerPublishTest.practitionerService
        }
    private val practitionerPublish = PractitionerPublish(mockk(), mockk(), mockk(), mockk())

    private val appointment1 =
        Appointment(
            id = Id("$tenantId-1234"),
            status = AppointmentStatus.BOOKED.asCode(),
            participant =
                listOf(
                    Participant(
                        actor = Reference(reference = FHIRString("Practitioner/$tenantId-1234")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                    Participant(
                        actor = Reference(reference = FHIRString("Practitioner/$tenantId-5678")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                ),
        )
    private val appointment2 =
        Appointment(
            id = Id("$tenantId-5678"),
            status = AppointmentStatus.BOOKED.asCode(),
            participant =
                listOf(
                    Participant(
                        actor = Reference(reference = FHIRString("Practitioner/$tenantId-9012")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                ),
        )
    private val appointment3 =
        Appointment(
            id = Id("$tenantId-9012"),
            status = AppointmentStatus.BOOKED.asCode(),
            participant =
                listOf(
                    Participant(
                        actor = Reference(reference = FHIRString("Location/$tenantId-3456")),
                        status = ParticipationStatus.ACCEPTED.asCode(),
                    ),
                ),
        )
    private val metadata =
        mockk<Metadata>(relaxed = true) {
            every { runId } returns "run"
        }

    @Test
    fun `publish events create a AppointmentPublishPractitionerRequest`() {
        val publishEvent =
            mockk<InteropResourcePublishV1> {
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(appointment1)
                every { metadata } returns this@PractitionerPublishTest.metadata
            }
        val request = practitionerPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(PractitionerPublish.AppointmentPublishPractitionerRequest::class.java, request)
    }

    @Test
    fun `load events create a LoadPractitionerRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = practitionerPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(PractitionerPublish.LoadPractitionerRequest::class.java, request)
    }

    @Test
    fun `PatientPublishPractitionerRequest supports loads resources`() {
        val practitioner1 = mockk<Practitioner>()
        val practitioner2 = mockk<Practitioner>()
        val practitioner3 = mockk<Practitioner>()
        every {
            practitionerService.getByIDs(
                tenant,
                listOf("1234", "5678", "9012"),
            )
        } returns mapOf("1234" to practitioner1, "5678" to practitioner2, "9012" to practitioner3)

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
            PractitionerPublish.AppointmentPublishPractitionerRequest(
                listOf(event1, event2, event3),
                practitionerService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Practitioner, tenant, "$tenantId-1234")
        assertEquals(listOf(practitioner1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Practitioner, tenant, "$tenantId-5678")
        assertEquals(listOf(practitioner2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Practitioner, tenant, "$tenantId-9012")
        assertEquals(listOf(practitioner3), resourcesByKeys[key3])
    }
}
