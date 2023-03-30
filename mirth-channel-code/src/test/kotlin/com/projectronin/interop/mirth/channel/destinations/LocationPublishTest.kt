package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.fhir.r4.valueset.ParticipationStatus
import com.projectronin.interop.mirth.connector.util.asCode
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LocationPublishTest {
    lateinit var tenant: Tenant
    lateinit var destination: LocationPublish

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        destination = LocationPublish(mockk(), mockk(), mockk(), mockk(), mockk())
        mockkObject(JacksonUtil)
    }

    @AfterEach
    fun unmockk() {
        unmockkAll()
    }

    @Test
    fun `channel creation works`() {
        assertNotNull(destination)
    }

    @Test
    fun `fails on unknown request`() {
        assertThrows<IllegalStateException> {
            destination.convertEventToRequest("nothing", "nothing", mockk(), mockk())
        }
    }

    @Test
    fun `works for load events`() {
        val event = InteropResourceLoadV1(
            "tenant",
            "id",
            "location",
            InteropResourceLoadV1.DataTrigger.adhoc
        )
        val mockLocation = mockk<Location>()
        every { JacksonUtil.readJsonObject("nothing", InteropResourceLoadV1::class) } returns event
        val mockVendorFactory = mockk<VendorFactory> {
            every { locationService.getByID(tenant, "id") } returns mockLocation
        }
        val request = destination.convertEventToRequest(
            "nothing",
            InteropResourceLoadV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        val results = request.loadResources()
        assertEquals(mockLocation, results.first())
    }

    @Test
    fun `works for publish events`() {
        val event = InteropResourcePublishV1(
            "tenant",
            "location",
            InteropResourcePublishV1.DataTrigger.adhoc,
            "123"
        )
        val mockApptParticipant = mockk<Appointment> {
            every { participant } returns listOf(
                Participant(
                    status = ParticipationStatus.ACCEPTED.asCode(),
                    actor = Reference(reference = "Location/1".asFHIR())
                ),
                Participant(
                    status = ParticipationStatus.ACCEPTED.asCode(),
                    actor = Reference(reference = "Location/1".asFHIR())
                )
            )
        }
        val mockLocation = mockk<Location> { every { id?.value } returns "Location/1" }
        every { JacksonUtil.readJsonObject("123", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("123", mockApptParticipant::class) } returns mockApptParticipant
        val mockVendorFactory = mockk<VendorFactory> {
            every { locationService.getLocationsByFHIRId(tenant, listOf("1")) } returns
                mapOf("id" to mockLocation)
        }
        val request = destination.convertEventToRequest(
            "123",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        val results = request.loadResources()
        assertEquals(mockLocation, results.first())
    }
}