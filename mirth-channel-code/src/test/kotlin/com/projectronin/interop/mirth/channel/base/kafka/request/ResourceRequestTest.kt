package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.collection.mapListValues
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ResourceRequestTest {
    private val fhirService = mockk<FHIRService<Location>>()
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns "tenant"
    }

    @Test
    fun `returns runId`() {
        val event = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { metadata.runId } returns "run1234"
        }

        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        assertEquals("run1234", request.runId)
    }

    @Test
    fun `associates request keys to events for single event`() {
        val key1 = mockk<ResourceRequestKey>()
        val key2 = mockk<ResourceRequestKey>()

        val event = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { requestKeys } returns setOf(key1, key2)
        }

        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        val eventsByRequestKey = request.eventsByRequestKey
        assertEquals(2, eventsByRequestKey.size)
        assertEquals(event, eventsByRequestKey[key1])
        assertEquals(event, eventsByRequestKey[key2])
    }

    @Test
    fun `associates request keys to events for multiple events`() {
        val key1 = mockk<ResourceRequestKey>()
        val key2 = mockk<ResourceRequestKey>()
        val key3 = mockk<ResourceRequestKey>()

        val event1 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { requestKeys } returns setOf(key1, key2)
        }
        val event2 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { requestKeys } returns setOf(key3)
        }

        val request = TestResourceRequest(listOf(event1, event2), fhirService, tenant)
        val eventsByRequestKey = request.eventsByRequestKey
        assertEquals(3, eventsByRequestKey.size)
        assertEquals(event1, eventsByRequestKey[key1])
        assertEquals(event1, eventsByRequestKey[key2])
        assertEquals(event2, eventsByRequestKey[key3])
    }

    @Test
    fun `returns request keys for single event`() {
        val key1 = mockk<ResourceRequestKey>()
        val key2 = mockk<ResourceRequestKey>()

        val event = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { requestKeys } returns setOf(key1, key2)
        }

        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        assertEquals(setOf(key1, key2), request.requestKeys)
    }

    @Test
    fun `returns request keys for multiple events`() {
        val key1 = mockk<ResourceRequestKey>()
        val key2 = mockk<ResourceRequestKey>()
        val key3 = mockk<ResourceRequestKey>()

        val event1 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { requestKeys } returns setOf(key1, key2)
        }
        val event2 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { requestKeys } returns setOf(key3)
        }

        val request = TestResourceRequest(listOf(event1, event2), fhirService, tenant)
        assertEquals(setOf(key1, key2, key3), request.requestKeys)
    }

    @Test
    fun `returns source references for single event`() {
        val reference1 = mockk<Metadata.UpstreamReference>()

        val event = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { getSourceReference() } returns reference1
        }

        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        assertEquals(listOf(reference1), request.sourceReferences)
    }

    @Test
    fun `returns source references for multiple events`() {
        val reference1 = mockk<Metadata.UpstreamReference>()
        val reference2 = mockk<Metadata.UpstreamReference>()

        val event1 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { getSourceReference() } returns reference1
        }
        val event2 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { getSourceReference() } returns reference2
        }

        val request = TestResourceRequest(listOf(event1, event2), fhirService, tenant)
        assertEquals(listOf(reference1, reference2), request.sourceReferences)
    }

    @Test
    fun `returns source references when some events have none`() {
        val reference1 = mockk<Metadata.UpstreamReference>()
        val reference2 = mockk<Metadata.UpstreamReference>()

        val event1 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { getSourceReference() } returns reference1
        }
        val event2 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { getSourceReference() } returns reference2
        }
        val event3 = mockk<ResourceEvent<InteropResourcePublishV1>> {
            every { getSourceReference() } returns null
        }

        val request = TestResourceRequest(listOf(event1, event2, event3), fhirService, tenant)
        assertEquals(listOf(reference1, reference2), request.sourceReferences)
    }

    @Test
    fun `loads resources for request keys where all are found`() {
        val location1 = mockk<Location>()
        val location2 = mockk<Location>()

        every { fhirService.getByIDs(tenant, listOf("fhirId1", "fhirId2")) } returns mapOf(
            "fhirId1" to location1,
            "fhirId2" to location2
        )

        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestResourceRequest(listOf(event), fhirService, tenant)

        val key1 = mockk<ResourceRequestKey> {
            every { unlocalizedResourceId } returns "fhirId1"
        }
        val key2 = mockk<ResourceRequestKey> {
            every { unlocalizedResourceId } returns "fhirId2"
        }

        val resources = request.loadResources(listOf(key1, key2))
        assertEquals(2, resources.size)
        assertEquals(listOf(location1), resources[key1])
        assertEquals(listOf(location2), resources[key2])
    }

    @Test
    fun `loads resources for request keys where some are found`() {
        val location1 = mockk<Location>()

        every { fhirService.getByIDs(tenant, listOf("fhirId1", "fhirId2")) } returns mapOf(
            "fhirId1" to location1
        )

        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestResourceRequest(listOf(event), fhirService, tenant)

        val key1 = mockk<ResourceRequestKey> {
            every { unlocalizedResourceId } returns "fhirId1"
        }
        val key2 = mockk<ResourceRequestKey> {
            every { unlocalizedResourceId } returns "fhirId2"
        }

        val resources = request.loadResources(listOf(key1, key2))
        assertEquals(1, resources.size)
        assertEquals(listOf(location1), resources[key1])
    }

    @Test
    fun `loads resources for request keys where none are found`() {
        every { fhirService.getByIDs(tenant, listOf("fhirId1", "fhirId2")) } returns emptyMap()

        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestResourceRequest(listOf(event), fhirService, tenant)

        val key1 = mockk<ResourceRequestKey> {
            every { unlocalizedResourceId } returns "fhirId1"
        }
        val key2 = mockk<ResourceRequestKey> {
            every { unlocalizedResourceId } returns "fhirId2"
        }

        val resources = request.loadResources(listOf(key1, key2))
        assertEquals(0, resources.size)
    }

    @Test
    fun `does not skip all publishing`() {
        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        assertFalse(request.skipAllPublishing)
    }

    @Test
    fun `does not skip kafka publishing`() {
        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        assertFalse(request.skipKafkaPublishing)
    }

    class TestResourceRequest(
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>>,
        override val fhirService: FHIRService<Location>,
        override val tenant: Tenant
    ) : ResourceRequest<Location, InteropResourcePublishV1>() {
        override val dataTrigger: DataTrigger
            get() = TODO("Not yet implemented")

        override fun loadResourcesForIds(requestFhirIds: List<String>): Map<String, List<Location>> {
            return fhirService.getByIDs(tenant, requestFhirIds).mapListValues()
        }
    }
}
