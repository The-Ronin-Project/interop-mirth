package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime

class PublishResourceRequestTest {
    private val fhirService = mockk<FHIRService<Location>>()
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }

    @Test
    fun `sets dataTrigger for ad-hoc events`() {
        val publishEvent =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { sourceEvent.dataTrigger } returns InteropResourcePublishV1.DataTrigger.adhoc
            }

        val request = TestPublishResourceRequest(listOf(publishEvent), fhirService, tenant)
        assertEquals(DataTrigger.AD_HOC, request.dataTrigger)
    }

    @Test
    fun `sets dataTrigger for nightly events`() {
        val publishEvent =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { sourceEvent.dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            }

        val request = TestPublishResourceRequest(listOf(publishEvent), fhirService, tenant)
        assertEquals(DataTrigger.NIGHTLY, request.dataTrigger)
    }

    @Test
    fun `sets dataTrigger for backfill events`() {
        val publishEvent =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { sourceEvent.dataTrigger } returns InteropResourcePublishV1.DataTrigger.backfill
            }

        val request = TestPublishResourceRequest(listOf(publishEvent), fhirService, tenant)
        assertEquals(DataTrigger.BACKFILL, request.dataTrigger)
    }

    @Test
    fun `throws exception for unknown data trigger`() {
        val publishEvent =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { sourceEvent.dataTrigger } returns null
            }

        val request = TestPublishResourceRequest(listOf(publishEvent), fhirService, tenant)
        val exception = assertThrows<IllegalStateException> { request.dataTrigger }
        assertEquals(
            "Received a null data trigger which cannot be transformed to a known value",
            exception.message,
        )
    }

    class TestPublishResourceRequest(
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>>,
        override val fhirService: FHIRService<Location>,
        override val tenant: Tenant,
    ) : PublishResourceRequest<Location>() {
        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<Location>> {
            TODO("Not yet implemented")
        }
    }
}
