package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.AppointmentService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninAppointment
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppointmentPublishTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var transformManager: TransformManager
    lateinit var publishService: PublishService
    lateinit var apptService: AppointmentService
    lateinit var roninAppointment: RoninAppointment
    lateinit var writer: AppointmentPublish

    private val tenant = mockk<Tenant>() {
        every { mnemonic } returns "mockTenant"
    }

    @AfterEach
    fun unMock() {
        unmockkAll()
    }

    @BeforeEach
    fun setup() {
        apptService = mockk()
        vendorFactory = mockk {
            every { appointmentService } returns apptService
        }

        transformManager = mockk()
        publishService = mockk()
        roninAppointment = mockk()
        mockkObject(JacksonUtil)
        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic("mockTenant") } returns tenant
            every { getTenantForMnemonic("nothing") } returns null
        }

        val ehrFactory = mockk<EHRFactory>() {
            every { getVendorFactory(tenant) } returns vendorFactory
        }

        writer = AppointmentPublish(
            tenantService, ehrFactory, transformManager, roninAppointment, publishService
        )
    }

    @Test
    fun `check that writer works`() {
        val fakeResource = "fakeResource"
        val fakePatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "fakePatientId"
            }
        }

        val fakeAppointment = mockk<Appointment> {
            every { id } returns mockk {
                every { value } returns "fakePatientId"
            }
        }

        val transformedAppt = mockk<Appointment>() { }

        val fakeAppointmentEvent = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.adhoc
            every { resourceJson } returns fakeResource
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourcePublishV1::class) } returns fakeAppointmentEvent
        every { JacksonUtil.readJsonObject(fakeResource, Patient::class) } returns fakePatient
        every { JacksonUtil.writeJsonValue(listOf(transformedAppt)) } returns "nothing to see here"

        every {
            apptService.findPatientAppointments(
                tenant, "fakePatientId", any(), any()
            )
        } returns listOf(
            fakeAppointment
        )
        every { transformManager.transformResource(fakeAppointment, roninAppointment, tenant) } returns transformedAppt
        every { publishService.publishFHIRResources("mockTenant", listOf(transformedAppt), DataTrigger.AD_HOC) } returns true

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("nothing to see here", result.detailedMessage)
        assertEquals("Published 1 appointment(s)", result.message)
    }

    @Test
    fun `writer fails when bad tenant`() {
        assertThrows<IllegalArgumentException> {
            writer.channelDestinationWriter("nothing", "fake event", emptyMap(), emptyMap())
        }
    }

    @Test
    fun `writer fails when can't serialize event`() {
        val exception = assertThrows<IllegalStateException> {
            writer.channelDestinationWriter(
                "mockTenant",
                "fake event",
                mapOf(MirthKey.KAFKA_EVENT.code to "Bad event"),
                emptyMap()
            )
        }
        assertEquals("Received a string which cannot deserialize to a known event", exception.message)
    }

    @Test
    fun `writer fails when didn't pass event name`() {
        val exception = assertThrows<MapVariableMissing> {
            writer.channelDestinationWriter("mockTenant", "fake event", emptyMap(), emptyMap())
        }
        assertEquals("Missing Event Name", exception.message)
    }

    @Test
    fun `writer returns publish errors when found`() {
        val fakeResource = "fakeResource"
        val fakePatient = mockk<Patient> {
            every { id } returns mockk {
                every { value } returns "patientId"
            }
        }
        val fakeAppointment1 = mockk<Appointment> { every { id?.value } returns "1" }
        val fakeAppointment2 = mockk<Appointment> { every { id?.value } returns "2" }
        val fakeAppointment3 = mockk<Appointment> { every { id?.value } returns "3" }
        val fakeAppointment4 = mockk<Appointment> { every { id?.value } returns "4" }
        val fakeAppointment5 = mockk<Appointment> { every { id?.value } returns "5" }
        val fakeAppointment6 = mockk<Appointment> { every { id?.value } returns "6" }

        val fakeAppointmentList = listOf(
            fakeAppointment1, fakeAppointment2, fakeAppointment3, fakeAppointment4, fakeAppointment5, fakeAppointment6
        )
        val fakeEvent = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { resourceJson } returns fakeResource
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourcePublishV1::class) } returns fakeEvent
        every { JacksonUtil.readJsonObject(fakeResource, Patient::class) } returns fakePatient
        every { JacksonUtil.writeJsonValue(listOf("1", "2", "3", "4", "5", "6")) } returns "properly truncated"
        every {
            apptService.findPatientAppointments(
                tenant, "patientId", any(), any()
            )
        } returns fakeAppointmentList

        every { transformManager.transformResource(fakeAppointment1, roninAppointment, tenant) } returns fakeAppointment1
        every { transformManager.transformResource(fakeAppointment2, roninAppointment, tenant) } returns fakeAppointment2
        every { transformManager.transformResource(fakeAppointment3, roninAppointment, tenant) } returns fakeAppointment3
        every { transformManager.transformResource(fakeAppointment4, roninAppointment, tenant) } returns fakeAppointment4
        every { transformManager.transformResource(fakeAppointment5, roninAppointment, tenant) } returns fakeAppointment5
        every { transformManager.transformResource(fakeAppointment6, roninAppointment, tenant) } returns fakeAppointment6

        every {
            publishService.publishFHIRResources("mockTenant", fakeAppointmentList, DataTrigger.NIGHTLY)
        } returns false

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("properly truncated", result.detailedMessage)
        assertEquals("Failed to publish 6 appointment(s)", result.message)
    }

    @Test
    fun `writer returns failed ehr calls`() {
        val appointment = mockk<Appointment> { every { id?.value } returns "id" }
        val mockApptEvent = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.backfill
            every { resourceFHIRId } returns "youAskedForThis"
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourceLoadV1::class) } returns mockApptEvent
        every { JacksonUtil.writeJsonValue(listOf(appointment)) } returns "resource"

        every { apptService.getByID(tenant, "youAskedForThis") } throws Exception("EHR is toast")
        every { transformManager.transformResource(appointment, roninAppointment, tenant) } returns null

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("EHR is toast", result.detailedMessage)
        assertEquals("Failed EHR Call", result.message)
    }

    @Test
    fun `writer surfaces transform errors`() {
        val appointment = mockk<Appointment> { every { id?.value } returns "id" }

        val mockEvent = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.backfill
            every { resourceFHIRId } returns "youAskedForThis"
        }

        every { JacksonUtil.readJsonObject("fake event", InteropResourceLoadV1::class) } returns mockEvent
        every { JacksonUtil.writeJsonValue(listOf(appointment)) } returns "resource"

        every { apptService.getByID(tenant, "youAskedForThis") } returns appointment
        every { transformManager.transformResource(appointment, roninAppointment, tenant) } returns null

        val result = writer.channelDestinationWriter(
            "mockTenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("resource", result.detailedMessage)
        assertEquals("Failed to transform 1 appointment(s)", result.message)
    }
}
