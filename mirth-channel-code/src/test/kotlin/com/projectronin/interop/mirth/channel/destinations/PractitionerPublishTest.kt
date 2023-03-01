package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.fhir.r4.resource.Practitioner
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

class PractitionerPublishTest {
    lateinit var tenant: Tenant
    lateinit var destination: PractitionerPublish

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        destination = PractitionerPublish(mockk(), mockk(), mockk(), mockk(), mockk())
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
            destination.convertEventToRequest("boo", "", mockk(), mockk())
        }
    }

    @Test
    fun `works for load events`() {
        val event = InteropResourceLoadV1(
            "tenant",
            "id",
            "condition",
            InteropResourceLoadV1.DataTrigger.adhoc
        )
        val mockPractitioner = mockk<Practitioner>()
        every { JacksonUtil.readJsonObject("boo", InteropResourceLoadV1::class) } returns event
        val mockVendorFactory = mockk<VendorFactory> {
            every { practitionerService.getByID(tenant, "id") } returns mockPractitioner
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourceLoadV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        val results = request.loadResources()
        assertEquals(mockPractitioner, results.first())
    }

    @Test
    fun `works for publish events`() {
        val event = InteropResourcePublishV1(
            "tenant",
            "appointment",
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
        )
        val mockParticipant = mockk<Participant> {
            every { actor?.reference?.value } returns "Practitioner/456"
        }
        val mockAppointment = mockk<Appointment> {
            every { id?.value } returns "123"
            every { participant } returns listOf(mockParticipant)
        }
        val mockPractitioner = mockk<Practitioner> {
            // every { id?.value } returns "456"
        }
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", Appointment::class) } returns mockAppointment
        val mockVendorFactory = mockk<VendorFactory> {
            every { practitionerService.getPractitioner(tenant, "456") } returns
                mockPractitioner
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )
        val results = request.loadResources()
        assertEquals(mockPractitioner, results.first())
    }
}
