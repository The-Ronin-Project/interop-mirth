package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.destinations.PatientPublish
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PatientLoadTest {
    lateinit var tenant: Tenant
    lateinit var tenantService: TenantService
    lateinit var vendorFactory: VendorFactory
    lateinit var loadService: KafkaLoadService
    lateinit var channel: PatientLoad

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "ronin"
        }
        vendorFactory = mockk()
        loadService = mockk()
        tenantService = mockk {
            every { getTenantForMnemonic("ronin") } returns tenant
        }
        val ehrFactory = mockk<EHRFactory> {
            every { getVendorFactory(tenant) } returns vendorFactory
        }
        val dest = mockk<PatientPublish>()
        channel = PatientLoad(
            tenantService,
            ehrFactory,
            loadService,
            dest
        )
    }

    @Test
    fun `codecov`() {
        assertEquals("PatientLoad", channel.rootName)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `sourceReader works`() {
        every { loadService.retrieveLoadEvents(ResourceType.PATIENT) } returns listOf(
            InteropResourceLoadV1(
                tenantId = tenant.mnemonic,
                resourceType = "Patient",
                resourceFHIRId = "123",
                dataTrigger = InteropResourceLoadV1.DataTrigger.nightly
            ),
            InteropResourceLoadV1(
                tenantId = tenant.mnemonic,
                resourceType = "Patient",
                resourceFHIRId = "456",
                dataTrigger = InteropResourceLoadV1.DataTrigger.adhoc
            ),
            InteropResourceLoadV1(
                tenantId = tenant.mnemonic,
                resourceType = "Patient",
                resourceFHIRId = "789",
                dataTrigger = InteropResourceLoadV1.DataTrigger.backfill
            ),
            InteropResourceLoadV1(
                tenantId = tenant.mnemonic,
                resourceType = "Patient",
                resourceFHIRId = "000"
            )
        )
        val list = channel.channelSourceReader(emptyMap())
        assertEquals(4, list.size)
        assertEquals("123", list.first().message)
        assertEquals("ronin", list.first().dataMap[MirthKey.TENANT_MNEMONIC.code])
        assertEquals(DataTrigger.NIGHTLY, list.first().dataMap[MirthKey.DATA_TRIGGER.code])
    }

    @Test
    fun `sourceTransform - works`() {
        val patient = mockk<Patient> {
            every { id!!.value } returns "123"
        }
        mockkObject(JacksonUtil)
        every { JacksonUtil.writeJsonValue(any()) } returns "json"
        every { vendorFactory.patientService.getPatient(tenant, any()) } returns patient
        val message = channel.channelSourceTransformer(
            "ronin",
            "123",
            mapOf(MirthKey.DATA_TRIGGER.code to DataTrigger.NIGHTLY),
            emptyMap()
        )
        assertNotNull(message.message)
        unmockkObject(JacksonUtil)
    }

    @Test
    fun `sourceTransform -  bad tenant throws exception`() {
        every { tenantService.getTenantForMnemonic("no") } returns null
        assertThrows<Exception> {
            channel.channelSourceTransformer("no", "[\"123\"]", emptyMap(), emptyMap())
        }
    }
}
