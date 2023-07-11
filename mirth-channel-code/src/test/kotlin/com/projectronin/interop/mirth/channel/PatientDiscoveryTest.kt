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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
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
            every { getMonitoredTenants() } returns listOf(tenant, tenant2)
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
    fun `sourceReader works`() {
        val list = channel.channelSourceReader(emptyMap())
        assertEquals(2, list.size)
        assertEquals("123", list.first().message)
        assertEquals("456", list[1].message)
        assertEquals("ronin", list.first().dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals("ronin", list[1].dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertNotNull(list[1].dataMap[MirthKey.EVENT_RUN_ID.code])
    }

    @Test
    fun `tenants that already ran shouldn't run again`() {
        every { tenantConfigurationService.getConfiguration(any()) } returns mockk {
            every { lastUpdated } returns OffsetDateTime.now().plusHours(2)
        }
        val list = channel.channelSourceReader(emptyMap())
        assertEquals(0, list.size)
    }

    @Test
    fun `tenants without config aren't asked to run`() {
        every { tenantConfigurationService.getConfiguration(any()) } throws IllegalArgumentException("oops")
        val list = channel.channelSourceReader(emptyMap())
        assertEquals(0, list.size)
    }

    @Test
    fun `AvailableWindow inits correctly with blank values`() {
        val tenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns null
        }
        val availableWindow = PatientDiscovery.AvailableWindow(tenant)
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        assertEquals(OffsetTime.of(0, 0, 0, 0, zone), availableWindow.windowStartTime)
        assertEquals(OffsetTime.of(23, 59, 59, 999999999, zone), availableWindow.windowEndTime)
        assertFalse(availableWindow.spansMidnight)
    }

    @Test
    fun `AvailableWindow inits correctly with passed values`() {
        val tenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(20, 0)
                every { availableEnd } returns LocalTime.of(3, 0)
            }
        }
        val availableWindow = PatientDiscovery.AvailableWindow(tenant)
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        assertEquals(OffsetTime.of(20, 0, 0, 0, zone), availableWindow.windowStartTime)
        assertEquals(OffsetTime.of(3, 0, 0, 0, zone), availableWindow.windowEndTime)
        assertTrue(availableWindow.spansMidnight)
    }

    @Test
    fun `AvailableWindow can correctly determine the right window`() {
        val tenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(1, 30)
                every { availableEnd } returns LocalTime.of(7, 0)
            }
        }
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        val availableWindow = PatientDiscovery.AvailableWindow(tenant)
        val oneAm = OffsetDateTime.of(2023, 4, 1, 1, 0, 0, 0, zone)
        val twoAm = oneAm.withHour(2)
        val eightAm = oneAm.withHour(8)
        val sixPm = oneAm.withHour(18)
        val midNight = oneAm.withHour(0)

        assertFalse(availableWindow.isInWindow(oneAm))
        assertTrue(availableWindow.isInWindow(twoAm))
        assertFalse(availableWindow.isInWindow(eightAm))
        assertFalse(availableWindow.isInWindow(sixPm))
        assertFalse(availableWindow.isInWindow(midNight))
    }

    @Test
    fun `AvailableWindow can correctly determine the right window for spanning midnight`() {
        val tenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(21, 0)
                every { availableEnd } returns LocalTime.of(3, 0)
            }
        }
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        val availableWindow = PatientDiscovery.AvailableWindow(tenant)
        val oneAm = OffsetDateTime.of(2023, 4, 1, 1, 0, 0, 0, zone)
        val twoAm = oneAm.withHour(1)
        val eightAm = oneAm.withHour(8)
        val sixPm = oneAm.withHour(18)
        val elevenPm = oneAm.withHour(23)
        val midNight = oneAm.withHour(0)

        assertTrue(availableWindow.isInWindow(oneAm))
        assertTrue(availableWindow.isInWindow(twoAm))
        assertFalse(availableWindow.isInWindow(eightAm))
        assertFalse(availableWindow.isInWindow(sixPm))
        assertTrue(availableWindow.isInWindow(elevenPm))
        assertTrue(availableWindow.isInWindow(midNight))
    }

    @Test
    fun `AvailableWindow can correctly determine when UTC is a different day`() {
        val tenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(21, 0)
                every { availableEnd } returns LocalTime.of(23, 0)
            }
        }
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        val availableWindow = PatientDiscovery.AvailableWindow(tenant)
        val utcZone = ZoneId.of("Etc/UTC").rules.getOffset(Instant.now())
        val currentTime = OffsetDateTime.of(2023, 4, 9, 21, 30, 0, 0, zone)
            .withOffsetSameInstant(utcZone)
        assertTrue(availableWindow.isInWindow(currentTime))
    }

    @Test
    fun `AvailableWindow can correctly determine if something ran when spans midnight`() {
        val spansMidnightTenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(20, 0)
                every { availableEnd } returns LocalTime.of(3, 0)
            }
        }
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        val spansMidnightAvailableWindow = PatientDiscovery.AvailableWindow(spansMidnightTenant)
        val earlyMorningRunToday = OffsetDateTime.of(2023, 4, 8, 0, 30, 0, 0, zone)

        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(earlyMorningRunToday, null))

        val eightAmYesterday = earlyMorningRunToday.withHour(8).minusDays(1)
        val elevenPMYesterday = earlyMorningRunToday.withHour(23).minusDays(1)
        val oneAmToday = earlyMorningRunToday.withHour(1)
        val eightAmToday = earlyMorningRunToday.withHour(8)
        val sixPmToday = earlyMorningRunToday.withHour(18)
        val elevenPmToday = earlyMorningRunToday.withHour(23)
        val eightAmTomorrow = eightAmToday.plusDays(1)
        val elevenPmTomorrow = elevenPmToday.plusDays(1)

        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(eightAmYesterday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(elevenPMYesterday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(oneAmToday, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(eightAmToday, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(sixPmToday, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(elevenPmToday, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(eightAmTomorrow, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(elevenPmTomorrow, earlyMorningRunToday))

        val lateNightRunYesterday = earlyMorningRunToday.withHour(23).withMinute(30).minusDays(1)
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(eightAmYesterday, lateNightRunYesterday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(elevenPMYesterday, lateNightRunYesterday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(oneAmToday, lateNightRunYesterday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(eightAmToday, lateNightRunYesterday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(sixPmToday, lateNightRunYesterday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(elevenPmToday, lateNightRunYesterday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(eightAmTomorrow, lateNightRunYesterday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(elevenPmTomorrow, lateNightRunYesterday))
    }

    @Test
    fun `AvailableWindow can correctly determine if something ran `() {
        val spansMidnightTenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(0, 30)
                every { availableEnd } returns LocalTime.of(7, 0)
            }
        }
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        val spansMidnightAvailableWindow = PatientDiscovery.AvailableWindow(spansMidnightTenant)
        val earlyMorningRunToday = OffsetDateTime.of(2023, 4, 8, 1, 30, 0, 0, zone)

        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(earlyMorningRunToday, null))

        val eightAmYesterday = earlyMorningRunToday.withHour(8).minusDays(1)
        val elevenPMYesterday = earlyMorningRunToday.withHour(23).minusDays(1)
        val oneAmToday = earlyMorningRunToday.withHour(1)
        val threeAmToday = earlyMorningRunToday.withHour(3)
        val eightAmToday = earlyMorningRunToday.withHour(8)
        val sixPmToday = earlyMorningRunToday.withHour(18)
        val elevenPmToday = earlyMorningRunToday.withHour(23)
        val eightAmTomorrow = eightAmToday.plusDays(1)
        val elevenPmTomorrow = elevenPmToday.plusDays(1)
        val threeAmTomorrow = threeAmToday.plusDays(1)

        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(eightAmYesterday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(elevenPMYesterday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(oneAmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(threeAmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(eightAmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(sixPmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(elevenPmToday, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(threeAmTomorrow, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(eightAmTomorrow, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(elevenPmTomorrow, earlyMorningRunToday))
    }

    @Test
    fun `AvailableWindow can correctly determine if something ran when passed UTC`() {
        val spansMidnightTenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(0, 30)
                every { availableEnd } returns LocalTime.of(7, 0)
            }
        }
        val utcZone = ZoneId.of("Etc/UTC").rules.getOffset(Instant.now())
        val clientTimeZone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        val spansMidnightAvailableWindow = PatientDiscovery.AvailableWindow(spansMidnightTenant)
        // this in utc, but starting from  Los Angeles time to match client for easy reading, then adjusting to utc
        val earlyMorningRunToday =
            OffsetDateTime.of(2023, 4, 8, 1, 30, 0, 0, clientTimeZone)
                .withOffsetSameInstant(utcZone)

        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(earlyMorningRunToday, null))

        val eightAmYesterday = earlyMorningRunToday.withHour(8).minusDays(1).withOffsetSameInstant(utcZone)
        val elevenPMYesterday = earlyMorningRunToday.withHour(23).minusDays(1).withOffsetSameInstant(utcZone)
        val oneAmToday = earlyMorningRunToday.withHour(1).withOffsetSameInstant(utcZone)
        val threeAmToday = earlyMorningRunToday.withHour(3).withOffsetSameInstant(utcZone)
        val eightAmToday = earlyMorningRunToday.withHour(8).withOffsetSameInstant(utcZone)
        val sixPmToday = earlyMorningRunToday.withHour(18).withOffsetSameInstant(utcZone)
        val elevenPmToday = earlyMorningRunToday.withHour(23).withOffsetSameInstant(utcZone)
        val eightAmTomorrow = earlyMorningRunToday.withHour(8).plusDays(1).withOffsetSameInstant(utcZone)
        val elevenPmTomorrow = earlyMorningRunToday.withHour(23).plusDays(1).withOffsetSameInstant(utcZone)
        val threeAmTomorrow = earlyMorningRunToday.withHour(3).plusDays(1).withOffsetSameInstant(utcZone)

        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(eightAmYesterday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(elevenPMYesterday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(oneAmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(threeAmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(eightAmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(sixPmToday, earlyMorningRunToday))
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(elevenPmToday, earlyMorningRunToday))
        // This is still "today" from the view of LA
        assertTrue(spansMidnightAvailableWindow.ranTodayAlready(threeAmTomorrow, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(eightAmTomorrow, earlyMorningRunToday))
        assertFalse(spansMidnightAvailableWindow.ranTodayAlready(elevenPmTomorrow, earlyMorningRunToday))
    }

    @Test
    fun `AvailableWindow can correctly determine when last run time is in UTC on a different day`() {
        val tenant = mockk<Tenant> {
            every { timezone } returns ZoneId.of("America/Los_Angeles")
            every { batchConfig } returns mockk {
                every { availableStart } returns LocalTime.of(21, 0)
                every { availableEnd } returns LocalTime.of(23, 0)
            }
        }
        val zone = ZoneId.of("America/Los_Angeles").rules.getOffset(Instant.now())
        val availableWindow = PatientDiscovery.AvailableWindow(tenant)
        val utcZone = ZoneId.of("Etc/UTC").rules.getOffset(Instant.now())
        val currentTime = OffsetDateTime.of(2023, 4, 9, 21, 30, 0, 0, zone)
            .withOffsetSameInstant(utcZone)
        val lastRunTime = OffsetDateTime.of(2023, 4, 9, 21, 0, 0, 0, zone).withOffsetSameInstant(utcZone)
        assertTrue(availableWindow.ranTodayAlready(currentTime, lastRunTime))
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
