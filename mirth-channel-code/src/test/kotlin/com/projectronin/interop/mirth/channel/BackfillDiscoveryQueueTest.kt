package com.projectronin.interop.mirth.channel

import com.projectronin.interop.backfill.client.DiscoveryQueueClient
import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueEntry
import com.projectronin.interop.backfill.client.generated.models.DiscoveryQueueStatus
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.AppointmentService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.outputs.AppointmentsWithNewPatients
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Participant
import com.projectronin.interop.mirth.channel.destinations.BackfillDiscoveryQueueWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.connector.util.asCode
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

class BackfillDiscoveryQueueTest {
    lateinit var tenant: Tenant
    lateinit var tenant2: Tenant
    lateinit var tenantService: TenantService
    lateinit var vendorFactory: VendorFactory
    lateinit var discoveryQueueClient: DiscoveryQueueClient
    lateinit var channel: BackfillDiscoveryQueue
    val backfillID = UUID.randomUUID()
    val start = LocalDate.now().minusMonths(10)
    val end = LocalDate.now()
    val queueEntry1 = DiscoveryQueueEntry(UUID.randomUUID(), "ronin", "123", start, end, backfillID, DiscoveryQueueStatus.UNDISCOVERED)
    val queueEntry2 = DiscoveryQueueEntry(UUID.randomUUID(), "ronin", "456", start, end, backfillID, DiscoveryQueueStatus.UNDISCOVERED)

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "ronin"
        }
        tenant2 = mockk {
            every { mnemonic } returns "blah"
        }
        discoveryQueueClient = mockk {
            coEvery { getDiscoveryQueueEntries("ronin", DiscoveryQueueStatus.UNDISCOVERED) } returns listOf(queueEntry1, queueEntry2)
            coEvery { getDiscoveryQueueEntries("blah", DiscoveryQueueStatus.UNDISCOVERED) } returns emptyList()
        }
        vendorFactory = mockk()

        tenantService = mockk {
            every { getTenantForMnemonic("ronin") } returns tenant
            every { getMonitoredTenants() } returns listOf(tenant, tenant2)
        }
        val ehrFactory = mockk<EHRFactory> {
            every { getVendorFactory(tenant) } returns vendorFactory
        }
        val writer = mockk<BackfillDiscoveryQueueWriter>()
        channel = BackfillDiscoveryQueue(
            tenantService,
            ehrFactory,
            discoveryQueueClient,
            writer
        )
    }

    @Test
    fun `codecov`() {
        assertEquals("BackfillDiscoveryQueue", channel.rootName)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `sourceReader works`() {
        val list = channel.channelSourceReader(emptyMap())
        assertEquals(2, list.size)
        assertEquals(JacksonUtil.writeJsonValue(queueEntry1), list.first().message)
        assertEquals("ronin", list.first().dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(backfillID.toString(), list.first().dataMap[MirthKey.BACKFILL_ID.code])
        assertEquals(backfillID.toString(), list.first().dataMap[MirthKey.EVENT_RUN_ID.code])
    }

    @Test
    fun `sourceReader errors don't cause it to crash`() {
        coEvery { discoveryQueueClient.getDiscoveryQueueEntries("blah", DiscoveryQueueStatus.UNDISCOVERED) } throws IllegalArgumentException("oops")
        val list = channel.channelSourceReader(emptyMap())
        assertEquals(2, list.size)
        assertEquals(JacksonUtil.writeJsonValue(queueEntry1), list.first().message)
        assertEquals("ronin", list.first().dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(backfillID.toString(), list.first().dataMap[MirthKey.BACKFILL_ID.code])
        assertEquals(backfillID.toString(), list.first().dataMap[MirthKey.EVENT_RUN_ID.code])
    }

    @Test
    fun `sourceTransform - works`() {
        val patient1 = Participant(
            status = com.projectronin.interop.fhir.r4.valueset.ParticipationStatus.ACCEPTED.asCode(),
            actor = Reference(
                reference = "Patient/patFhirID".asFHIR(),
                identifier = Identifier(value = "patientID".asFHIR(), system = Uri("system"))
            )
        )
        val location =
            Participant(
                status = com.projectronin.interop.fhir.r4.valueset.ParticipationStatus.ACCEPTED.asCode(),
                actor = Reference(reference = "Location".asFHIR())
            )
        val appt1 = Appointment(
            id = Id("1"),
            participant = listOf(location, patient1),
            status = com.projectronin.interop.fhir.r4.valueset.AppointmentStatus.BOOKED.asCode()
        )
        val appt2 = Appointment(
            id = Id("2"),
            participant = listOf(location, patient1),
            status = com.projectronin.interop.fhir.r4.valueset.AppointmentStatus.BOOKED.asCode()
        )
        val appointments = listOf(appt1, appt2)
        val findPractitionersResponse = AppointmentsWithNewPatients(appointments)

        val mockAppointmentService = mockk<AppointmentService> {
            every { findLocationAppointments(tenant, listOf("123"), start, end) } returns findPractitionersResponse
        }
        coEvery { discoveryQueueClient.updateDiscoveryQueueEntryByID(queueEntry1.id, any()) } returns true

        every { vendorFactory.appointmentService } returns mockAppointmentService

        val message = channel.channelSourceTransformer("ronin", JacksonUtil.writeJsonValue(queueEntry1), emptyMap(), emptyMap())
        assertEquals("[\"Patient/patFhirID\"]", message.message)
    }

    @Test
    fun `sourceTransform -  bad tenant throws exception`() {
        coEvery { discoveryQueueClient.updateDiscoveryQueueEntryByID(queueEntry1.id, any()) } returns true
        every { tenantService.getTenantForMnemonic("no") } returns null
        val exception = assertThrows<Exception> {
            channel.channelSourceTransformer("no", JacksonUtil.writeJsonValue(queueEntry1), emptyMap(), emptyMap())
        }
        assertEquals("No Tenant Found", exception.message)
    }
}
