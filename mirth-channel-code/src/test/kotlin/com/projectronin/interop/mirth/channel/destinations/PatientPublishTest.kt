package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.PatientService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PatientPublishTest {
    private val tenant = mockk<Tenant>(relaxed = true)
    private val patientService = mockk<PatientService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { patientService } returns this@PatientPublishTest.patientService
    }
    private val patientPublish = PatientPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    @Test
    fun `publish events throw an exception`() {
        val publishEvent = mockk<InteropResourcePublishV1>()
        val exception = assertThrows<IllegalStateException> {
            patientPublish.convertPublishEventsToRequest(
                listOf(publishEvent),
                vendorFactory,
                tenant
            )
        }
        assertEquals("Patient does not listen to Publish Events", exception.message)
    }

    @Test
    fun `load events create a LoadPatientRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = patientPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(PatientPublish.LoadPatientRequest::class.java, request)
    }
}
