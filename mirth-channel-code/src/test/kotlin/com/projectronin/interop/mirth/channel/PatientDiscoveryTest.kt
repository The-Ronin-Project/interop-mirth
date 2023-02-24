package com.projectronin.interop.mirth.channel

import com.projectronin.interop.aidbox.PatientService
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
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.ParticipationStatus
import com.projectronin.interop.mirth.channel.destinations.PatientDiscoveryWriter
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.connector.util.asCode
import com.projectronin.interop.mirth.service.TenantConfigurationService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.data.model.MirthTenantConfigDO
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

class PatientDiscoveryTest {
    lateinit var tenant: Tenant
    lateinit var tenant2: Tenant
    lateinit var tenantService: TenantService
    lateinit var vendorFactory: VendorFactory
    lateinit var patientService: PatientService
    lateinit var channel: PatientDiscovery
    lateinit var tenantConfigurationService: TenantConfigurationService

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "ronin"
            every { batchConfig } returns mockk {
                every { availableEnd } returns LocalTime.MAX
                every { availableStart } returns LocalTime.MIN
            }
            every { timezone } returns ZoneId.of("Etc/UTC")
        }
        tenant2 = mockk {
            every { mnemonic } returns "blah"
            every { batchConfig } returns mockk {
                every { availableEnd } returns LocalTime.MAX
                every { availableStart } returns LocalTime.MIN
            }
            every { timezone } returns ZoneId.of("Etc/UTC")
        }
        vendorFactory = mockk()
        patientService = mockk()

        tenantService = mockk {
            every { getTenantForMnemonic("ronin") } returns tenant
            every { getAllTenants() } returns listOf(tenant, tenant2)
        }
        val ehrFactory = mockk<EHRFactory> {
            every { getVendorFactory(tenant) } returns vendorFactory
        }
        val configDO = mockk<MirthTenantConfigDO> {
            every { lastUpdated } returns OffsetDateTime.now().minusDays(2)
            every { lastUpdated = any() } returns mockk()
        }
        tenantConfigurationService = mockk() {
            every { getLocationIDsByTenant("ronin") } returns listOf("123", "456")
            every { getConfiguration("ronin") } returns configDO
            every { getLocationIDsByTenant("blah") } returns emptyList()
            every { getConfiguration("blah") } returns configDO
            every { updateConfiguration(any()) } just Runs
        }
        val writer = mockk<PatientDiscoveryWriter>()
        channel = PatientDiscovery(
            tenantService,
            writer,
            ehrFactory,
            tenantConfigurationService
        )
    }

    @Test
    fun `codecov`() {
        assertEquals("PatientDiscovery", channel.rootName)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `batch time null`() {
        val batchTenant = mockk<Tenant> {
            every { batchConfig } returns null
        }
        assertTrue(channel.isTimeInRange(batchTenant))
    }

    @Test
    fun `batch time outside of range`() {
        val batchTenant = mockk<Tenant> {
            every { batchConfig } returns mockk {
                every { availableEnd } returns LocalTime.now().plusHours(2)
                every { availableStart } returns LocalTime.now().plusHours(1)
            }
            every { timezone } returns ZoneId.of("Etc/UTC")
        }
        assertFalse(channel.isTimeInRange(batchTenant))
    }

    @Test
    fun `batch time over midnight`() {
        val batchTenant = mockk<Tenant> {
            every { batchConfig } returns mockk {
                every { availableEnd } returns LocalTime.now().plusHours(1)
                every { availableStart } returns LocalTime.now().plusHours(2)
            }
            every { timezone } returns ZoneId.of("Etc/UTC")
        }
        assertTrue(channel.isTimeInRange(batchTenant))
    }

    @Test
    fun `need load not in range`() {
        val batchTenant = mockk<Tenant> {
            every { batchConfig } returns mockk {
                every { availableEnd } returns LocalTime.now().plusHours(2)
                every { availableStart } returns LocalTime.now().plusHours(1)
            }
            every { timezone } returns ZoneId.of("Etc/UTC")
        }
        assertFalse(channel.needsLoad(batchTenant))
    }

    @Test
    fun `need load outdated`() {
        val batchTenant = mockk<Tenant> {
            every { batchConfig } returns null
            every { mnemonic } returns "ronin"
        }

        every { tenantConfigurationService.getConfiguration("ronin") } returns mockk {
            every { lastUpdated } returns OffsetDateTime.now().minusDays(2)
            every { lastUpdated = any() } returns mockk()
        }

        assertTrue(channel.needsLoad(batchTenant))
    }

    @Test
    fun `need load too soon`() {
        val batchTenant = mockk<Tenant> {
            every { batchConfig } returns null
            every { mnemonic } returns "ronin"
        }

        every { tenantConfigurationService.getConfiguration("ronin") } returns mockk {
            every { lastUpdated } returns OffsetDateTime.now().minusHours(15)
            every { lastUpdated = any() } returns mockk()
        }

        assertFalse(channel.needsLoad(batchTenant))
    }

    @Test
    fun `need load never loaded`() {
        val batchTenant = mockk<Tenant> {
            every { batchConfig } returns null
            every { mnemonic } returns "ronin"
        }

        every { tenantConfigurationService.getConfiguration("ronin") } returns mockk {
            every { lastUpdated } returns null
            every { lastUpdated = any() } returns mockk()
        }

        assertTrue(channel.needsLoad(batchTenant))
    }

    @Test
    fun `sourceReader works`() {
        val list = channel.channelSourceReader(emptyMap())
        assertEquals(2, list.size)
        assertEquals("123", list.first().message)
        assertEquals("456", list[1].message)
        assertEquals("ronin", list.first().dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("ronin", list[1].dataMap[MirthKey.TENANT_MNEMONIC.code])
    }

    @Test
    fun `sourceReader errors don't cause it to crash`() {
        every {
            tenantConfigurationService.getLocationIDsByTenant("blah")
        } throws Exception("oops!")

        val list = channel.channelSourceReader(emptyMap())
        assertEquals(2, list.size)
        assertEquals("123", list.first().message)
        assertEquals("456", list[1].message)
        assertEquals("ronin", list.first().dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("ronin", list[1].dataMap[MirthKey.TENANT_MNEMONIC.code])
    }

    @Test
    fun `sourceTransform - works`() {
        val patient1 = Participant(
            status = ParticipationStatus.ACCEPTED.asCode(),
            actor = Reference(
                reference = "Patient/patFhirID".asFHIR(),
                identifier = Identifier(value = "patientID".asFHIR(), system = Uri("system"))
            )
        )
        val location =
            Participant(
                status = ParticipationStatus.ACCEPTED.asCode(),
                actor = Reference(reference = "Location".asFHIR())
            )
        val appt1 = Appointment(
            id = Id("1"),
            participant = listOf(location, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appt2 = Appointment(
            id = Id("2"),
            participant = listOf(location, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appointments = listOf(appt1, appt2)
        val findPractitionersResponse = AppointmentsWithNewPatients(appointments)

        val mockAppointmentService = mockk<AppointmentService> {
            every { findLocationAppointments(tenant, listOf("123"), any(), any()) } returns findPractitionersResponse
        }

        every { vendorFactory.appointmentService } returns mockAppointmentService

        val message = channel.channelSourceTransformer("ronin", "123", emptyMap(), emptyMap())
        assertEquals("[\"Patient/patFhirID\"]", message.message)
    }

    @Test
    fun `sourceTransform -  bad tenant throws exception`() {
        every { tenantService.getTenantForMnemonic("no") } throws Exception("e")
        assertThrows<Exception> {
            channel.channelSourceTransformer("no", "[\"123\"]", emptyMap(), emptyMap())
        }
    }
}
