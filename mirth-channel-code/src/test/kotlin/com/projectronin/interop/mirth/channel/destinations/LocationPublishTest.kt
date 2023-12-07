package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.LocationService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.Coding
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Encounter
import com.projectronin.interop.fhir.r4.resource.EncounterLocation
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.EncounterStatus
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

class LocationPublishTest {
    private val tenantId = "tenant"
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns tenantId
        }
    private val locationService = mockk<LocationService>()
    private val vendorFactory =
        mockk<VendorFactory> {
            every { locationService } returns this@LocationPublishTest.locationService
        }
    private val locationPublish = LocationPublish(mockk(), mockk(), mockk(), mockk(), mockk())

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
        )
    private val encounter3 =
        Encounter(
            id = Id("$tenantId-1234"),
            `class` = Coding(display = FHIRString("class")),
            status = EncounterStatus.FINISHED.asCode(),
        )

    private val metadata =
        mockk<Metadata>(relaxed = true) {
            every { runId } returns "run"
        }

    @Test
    fun `publish events create a AppointmentPublishLocationRequest for appointment publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Appointment
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(appointment1)
                every { metadata } returns this@LocationPublishTest.metadata
            }
        val request = locationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(LocationPublish.AppointmentPublishLocationRequest::class.java, request)
    }

    @Test
    fun `publish events create a EncounterPublishLocationRequest for encounter publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Encounter
                every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(encounter1)
                every { metadata } returns this@LocationPublishTest.metadata
            }
        val request = locationPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(LocationPublish.EncounterPublishLocationRequest::class.java, request)
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent =
            mockk<InteropResourcePublishV1>(relaxed = true) {
                every { resourceType } returns ResourceType.Practitioner
            }
        val exception =
            assertThrows<IllegalStateException> {
                locationPublish.convertPublishEventsToRequest(
                    listOf(publishEvent),
                    vendorFactory,
                    tenant,
                )
            }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load locations",
            exception.message,
        )
    }

    @Test
    fun `load events create a LoadLocationRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = locationPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(LocationPublish.LoadLocationRequest::class.java, request)
    }

    @Test
    fun `AppointmentPublishLocationRequest supports loads resources`() {
        val location1 = mockk<Location>()
        val location2 = mockk<Location>()
        val location3 = mockk<Location>()
        every {
            locationService.getByIDs(tenant, listOf("1234", "5678", "9012"))
        } returns mapOf("1234" to location1, "5678" to location2, "9012" to location3)

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
            LocationPublish.AppointmentPublishLocationRequest(
                listOf(event1, event2, event3),
                locationService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Location, tenant, "$tenantId-1234")
        assertEquals(listOf(location1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Location, tenant, "$tenantId-5678")
        assertEquals(listOf(location2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Location, tenant, "$tenantId-9012")
        assertEquals(listOf(location3), resourcesByKeys[key3])
    }

    @Test
    fun `EncounterPublishLocationRequest supports loads resources`() {
        val location1 = mockk<Location>()
        val location2 = mockk<Location>()
        val location3 = mockk<Location>()
        every {
            locationService.getByIDs(
                tenant,
                listOf("1234", "5678", "9012"),
            )
        } returns mapOf("1234" to location1, "5678" to location2, "9012" to location3)

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
            LocationPublish.EncounterPublishLocationRequest(
                listOf(event1, event2, event3),
                locationService,
                tenant,
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Location, tenant, "$tenantId-1234")
        assertEquals(listOf(location1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Location, tenant, "$tenantId-5678")
        assertEquals(listOf(location2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Location, tenant, "$tenantId-9012")
        assertEquals(listOf(location3), resourcesByKeys[key3])
    }
}
