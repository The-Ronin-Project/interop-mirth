package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime

class LoadResourceRequestTest {
    private val fhirService = mockk<FHIRService<Location>>()
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns "tenant"
    }
    private val metadata = Metadata(runId = "run", runDateTime = OffsetDateTime.now())

    @Test
    fun `creates sourceEvents from single load event`() {
        val loadEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val request = TestLoadResourceRequest(listOf(loadEvent), tenant, fhirService)
        val sourceEvents = request.sourceEvents
        assertEquals(1, sourceEvents.size)
    }

    @Test
    fun `creates sourceEvents from multiple load events`() {
        val loadEvent1 = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val loadEvent2 = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "67890",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val request = TestLoadResourceRequest(listOf(loadEvent1, loadEvent2), tenant, fhirService)
        val sourceEvents = request.sourceEvents
        assertEquals(2, sourceEvents.size)
    }

    @Test
    fun `sets dataTrigger for ad-hoc events`() {
        val loadEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.adhoc,
            metadata = metadata
        )
        val request = TestLoadResourceRequest(listOf(loadEvent), tenant, fhirService)
        assertEquals(DataTrigger.AD_HOC, request.dataTrigger)
    }

    @Test
    fun `sets dataTrigger for nightly events`() {
        val loadEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val request = TestLoadResourceRequest(listOf(loadEvent), tenant, fhirService)
        assertEquals(DataTrigger.NIGHTLY, request.dataTrigger)
    }

    @Test
    fun `throws exception for unknown data trigger`() {
        val loadEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.backfill,
            metadata = metadata
        )
        val exception =
            assertThrows<IllegalStateException> { TestLoadResourceRequest(listOf(loadEvent), tenant, fhirService) }
        assertEquals(
            "Received a data trigger (backfill) which cannot be transformed to a known value",
            exception.message
        )
    }

    @Test
    fun `loadResourcesForIds requests from fhirService`() {
        val location = Location(id = Id("12345"))
        every { fhirService.getByIDs(tenant, listOf("12345")) } returns mapOf("12345" to location)

        val loadEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val request = TestLoadResourceRequest(listOf(loadEvent), tenant, fhirService)
        val resources = request.loadResourcesForIds(listOf("12345"))
        assertEquals(1, resources.size)
        assertEquals(listOf(location), resources["12345"])

        unmockkAll()
    }

    class TestLoadResourceRequest(
        loadEvents: List<InteropResourceLoadV1>,
        tenant: Tenant,
        override val fhirService: FHIRService<Location>
    ) :
        LoadResourceRequest<Location>(loadEvents, tenant)
}
