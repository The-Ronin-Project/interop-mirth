package com.projectronin.interop.mirth.channel

import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.AppointmentService
import com.projectronin.interop.ehr.IdentifierService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.outputs.FindPractitionerAppointmentsResponse
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.Participant
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.valueset.AppointmentStatus
import com.projectronin.interop.fhir.r4.valueset.ParticipationStatus
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.TenantConfigurationFactory
import com.projectronin.interop.mirth.connector.util.asCode
import com.projectronin.interop.tenant.config.exception.ResourcesNotFoundException
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val VALID_TENANT_ID = "mdaoc"
private const val CHANNEL_ROOT_NAME = "AppointmentByPractitionerLoad"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

class AppointmentByPractitionerNightlyLoadTest {
    lateinit var vendorFactory: VendorFactory
    lateinit var serviceFactory: ServiceFactory
    lateinit var tenantConfigurationFactory: TenantConfigurationFactory
    lateinit var channel: AppointmentByPractitionerNightlyLoad

    private val tenant = mockk<Tenant> {
        every { mnemonic } returns VALID_TENANT_ID
    }

    @BeforeEach
    fun setup() {
        vendorFactory = mockk()

        tenantConfigurationFactory = mockk()

        serviceFactory = mockk {
            every { getTenant(VALID_TENANT_ID) } returns tenant
            every { vendorFactory(tenant) } returns vendorFactory
            every { tenantConfigurationFactory() } returns tenantConfigurationFactory
        }

        channel = AppointmentByPractitionerNightlyLoad(serviceFactory)
    }

    @Test
    fun `sourceReader - works`() {
        val patient1 = Participant(
            status = ParticipationStatus.ACCEPTED.asCode(),
            actor = Reference(
                reference = "Patient",
                identifier = Identifier(value = "patientID", system = Uri("system"))
            )
        )
        val provider1 =
            Participant(status = ParticipationStatus.ACCEPTED.asCode(), actor = Reference(reference = "Practitioner"))
        val appt1 = Appointment(
            id = Id("1"),
            participant = listOf(provider1, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appt2 = Appointment(
            id = Id("2"),
            participant = listOf(provider1, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appointmentlist = listOf(appt1, appt2)
        val findPractitionersResponse = FindPractitionerAppointmentsResponse(appointmentlist)

        val mockPractitionerService = mockk<PractitionerService> {
            every { getPractitionersByTenant(VALID_TENANT_ID) } returns mapOf(
                "provfhirID1" to listOf(
                    mockk {
                        every { value } returns "nonFhir1"
                    }
                ),
                "provfhirID2" to listOf(
                    mockk {
                        every { value } returns "nonFhir2"
                    }
                )
            )
        }
        val mockAppointmentService = mockk<AppointmentService> {
            every { findProviderAppointments(tenant, any(), any(), any()) } returns findPractitionersResponse
        }
        val mockIdentifierService = mockk<IdentifierService> {
            every { getPractitionerProviderIdentifier(tenant, any()) } returns mockk<Identifier> {
                every { value } returns "abc"
            }
        }

        every { vendorFactory.appointmentService } returns mockAppointmentService
        every { vendorFactory.identifierService } returns mockIdentifierService

        every { serviceFactory.practitionerService() } returns mockPractitionerService

        val messageList = channel.sourceReader(VALID_DEPLOYED_NAME, emptyMap())
        assertEquals(1, messageList.size)
        assertEquals(3, channel.destinations.size)
    }

    @Test
    fun `sourceReader - patient without identifier value`() {
        val patient1 =
            Participant(status = ParticipationStatus.ACCEPTED.asCode(), actor = Reference(reference = "Patient"))
        val provider1 =
            Participant(status = ParticipationStatus.ACCEPTED.asCode(), actor = Reference(reference = "Practitioner"))
        val appt1 = Appointment(
            id = Id("1"),
            participant = listOf(provider1, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appt2 = Appointment(
            id = Id("2"),
            participant = listOf(provider1, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appointmentList = listOf(appt1, appt2)

        val mockPractitionerService = mockk<PractitionerService> {
            every { getPractitionersByTenant(VALID_TENANT_ID) } returns mapOf(
                "provfhirID1" to listOf(
                    mockk {
                        every { value } returns "nonFhir1"
                    }
                ),
                "provfhirID2" to listOf(
                    mockk {
                        every { value } returns "nonFhir2"
                    }
                )
            )
        }
        val mockAppointmentService = mockk<AppointmentService> {
            every {
                findProviderAppointments(
                    tenant,
                    any(),
                    any(),
                    any()
                )
            } returns FindPractitionerAppointmentsResponse(appointmentList)
        }
        val mockIdentifierService = mockk<IdentifierService> {
            every { getPractitionerProviderIdentifier(tenant, any()) } returns mockk<Identifier> {
                every { value } returns "abc"
            }
        }
        every { vendorFactory.appointmentService } returns mockAppointmentService
        every { vendorFactory.identifierService } returns mockIdentifierService

        every { serviceFactory.practitionerService() } returns mockPractitionerService

        val messageList = channel.sourceReader(VALID_DEPLOYED_NAME, emptyMap())
        assertEquals(1, messageList.size)
        assertEquals(3, channel.destinations.size)
    }

    @Test
    fun `sourceReader - empty providers`() {
        val appointmentList = listOf<Appointment>()
        val mockAppointmentService = mockk<AppointmentService> {
            every {
                findProviderAppointments(
                    tenant,
                    any(),
                    any(),
                    any()
                )
            } returns FindPractitionerAppointmentsResponse(appointmentList)
        }
        val mockPractitionerService = mockk<PractitionerService> {
            every { getPractitionersByTenant(VALID_TENANT_ID) } returns mapOf(
                "provfhirID1" to listOf(
                    mockk {
                        every { value } returns "nonFhir1"
                    }
                ),
                "provfhirID2" to listOf(
                    mockk {
                        every { value } returns "nonFhir2"
                    }
                )
            )
        }
        val mockIdentifierService = mockk<IdentifierService> {
            every { getPractitionerProviderIdentifier(tenant, any()) } returns mockk<Identifier> {
                every { value } returns null
            }
        }
        every { vendorFactory.identifierService } returns mockIdentifierService
        every { vendorFactory.appointmentService } returns mockAppointmentService

        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "{}"
        every { serviceFactory.practitionerService() } returns mockPractitionerService

        val messageList = channel.sourceReader(
            VALID_DEPLOYED_NAME,
            emptyMap()
        )
        assertEquals(0, messageList.size)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `sourceReader - incomplete actor or reference`() {
        val patient1 = Participant(
            status = ParticipationStatus.ACCEPTED.asCode(),
            actor = Reference(
                reference = "Patient",
                identifier = Identifier(value = "patientID", system = Uri("system"))
            )
        )
        val provider1 = Participant(
            status = ParticipationStatus.ACCEPTED.asCode(),
            actor = Reference()
        )
        val appt1 = Appointment(
            id = Id("1"),
            participant = listOf(provider1, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appointmentlist = listOf(appt1)
        val findPractitionersResponse = FindPractitionerAppointmentsResponse(appointmentlist)

        val mockPractitionerService = mockk<PractitionerService> {
            every { getPractitionersByTenant(VALID_TENANT_ID) } returns mapOf(
                "provfhirID1" to listOf(
                    mockk {
                        every { value } returns "nonFhir1"
                    }
                )
            )
        }
        val mockAppointmentService = mockk<AppointmentService> {
            every { findProviderAppointments(tenant, any(), any(), any()) } returns findPractitionersResponse
        }
        val mockIdentifierService = mockk<IdentifierService> {
            every { getPractitionerProviderIdentifier(tenant, any()) } returns mockk<Identifier> {
                every { value } returns "abc"
            }
        }

        every { vendorFactory.appointmentService } returns mockAppointmentService
        every { vendorFactory.identifierService } returns mockIdentifierService

        every { serviceFactory.practitionerService() } returns mockPractitionerService

        val messageList = channel.sourceReader(VALID_DEPLOYED_NAME, emptyMap())
        assertEquals(1, messageList.size)
        assertEquals(3, channel.destinations.size)
    }

    @Test
    fun `sourceReader - missing actor or reference`() {
        val patient1 = Participant(
            status = ParticipationStatus.ACCEPTED.asCode(),
            actor = Reference(
                identifier = Identifier(value = "patientID", system = Uri("system"))
            )
        )
        val provider1 = Participant(
            status = ParticipationStatus.ACCEPTED.asCode(),
        )
        val appt1 = Appointment(
            id = Id("2"),
            participant = listOf(provider1, patient1),
            status = AppointmentStatus.BOOKED.asCode()
        )
        val appointmentlist = listOf(appt1)
        val findPractitionersResponse = FindPractitionerAppointmentsResponse(appointmentlist)

        val mockPractitionerService = mockk<PractitionerService> {
            every { getPractitionersByTenant(VALID_TENANT_ID) } returns mapOf(
                "provfhirID1" to listOf(
                    mockk {
                        every { value } returns "nonFhir1"
                    }
                )
            )
        }
        val mockAppointmentService = mockk<AppointmentService> {
            every { findProviderAppointments(tenant, any(), any(), any()) } returns findPractitionersResponse
        }
        val mockIdentifierService = mockk<IdentifierService> {
            every { getPractitionerProviderIdentifier(tenant, any()) } returns mockk<Identifier> {
                every { value } returns "abc"
            }
        }

        every { vendorFactory.appointmentService } returns mockAppointmentService
        every { vendorFactory.identifierService } returns mockIdentifierService

        every { serviceFactory.practitionerService() } returns mockPractitionerService

        val messageList = channel.sourceReader(VALID_DEPLOYED_NAME, emptyMap())
        assertEquals(1, messageList.size)
        assertEquals(
            "Found Appointment with incomplete Patient reference",
            messageList[0].message
        )
    }

    @Test
    fun `sourceReader - no practitioners found`() {
        val mockPractitionerService = mockk<PractitionerService> {
            every { getPractitionersByTenant(any()) } returns emptyMap()
        }
        every { serviceFactory.practitionerService() } returns mockPractitionerService
        val exception = assertThrows<ResourcesNotFoundException> {
            channel.sourceReader(
                VALID_DEPLOYED_NAME,
                emptyMap()
            )
        }
        assertEquals(
            "No Practitioners found in clinical data store for tenant $VALID_TENANT_ID",
            exception.message
        )
    }
}