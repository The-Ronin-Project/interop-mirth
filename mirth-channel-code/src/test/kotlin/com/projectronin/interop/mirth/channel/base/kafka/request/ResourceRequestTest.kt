package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.collection.mapListValues
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.ehr.ObservationService
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Observation
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ResourceRequestTest {
    private val fhirService = mockk<FHIRService<Location>>()
    private val tenant =
        mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }

    @Test
    fun `returns runId`() {
        val event =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { metadata.runId } returns "run1234"
            }

        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        assertEquals("run1234", request.runId)
    }

    @Test
    fun `associates request keys to events for single event`() {
        val key1 = mockk<ResourceRequestKey>()
        val key2 = mockk<ResourceRequestKey>()

        val event =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
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

        val event1 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { requestKeys } returns setOf(key1, key2)
            }
        val event2 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
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

        val event =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
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

        val event1 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { requestKeys } returns setOf(key1, key2)
            }
        val event2 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { requestKeys } returns setOf(key3)
            }

        val request = TestResourceRequest(listOf(event1, event2), fhirService, tenant)
        assertEquals(setOf(key1, key2, key3), request.requestKeys)
    }

    @Test
    fun `returns source references for single event`() {
        val reference1 = mockk<Metadata.UpstreamReference>()

        val event =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { getSourceReference() } returns reference1
            }

        val request = TestResourceRequest(listOf(event), fhirService, tenant)
        assertEquals(listOf(reference1), request.sourceReferences)
    }

    @Test
    fun `returns source references for multiple events`() {
        val reference1 = mockk<Metadata.UpstreamReference>()
        val reference2 = mockk<Metadata.UpstreamReference>()

        val event1 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { getSourceReference() } returns reference1
            }
        val event2 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { getSourceReference() } returns reference2
            }

        val request = TestResourceRequest(listOf(event1, event2), fhirService, tenant)
        assertEquals(listOf(reference1, reference2), request.sourceReferences)
    }

    @Test
    fun `returns source references when some events have none`() {
        val reference1 = mockk<Metadata.UpstreamReference>()
        val reference2 = mockk<Metadata.UpstreamReference>()

        val event1 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { getSourceReference() } returns reference1
            }
        val event2 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { getSourceReference() } returns reference2
            }
        val event3 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { getSourceReference() } returns null
            }

        val request = TestResourceRequest(listOf(event1, event2, event3), fhirService, tenant)
        assertEquals(listOf(reference1, reference2), request.sourceReferences)
    }

    @Test
    fun `loads resources for request keys where all are found`() {
        val location1 = mockk<Location>()
        val location2 = mockk<Location>()

        every { fhirService.getByIDs(tenant, listOf("fhirId1", "fhirId2")) } returns
            mapOf(
                "fhirId1" to location1,
                "fhirId2" to location2,
            )

        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestResourceRequest(listOf(event), fhirService, tenant)

        val key1 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId1"
                every { dateRange } returns null
            }
        val key2 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId2"
                every { dateRange } returns null
            }

        val resources = request.loadResources(listOf(key1, key2))
        assertEquals(2, resources.size)
        assertEquals(listOf(location1), resources[key1])
        assertEquals(listOf(location2), resources[key2])
    }

    @Test
    fun `loads resources for request keys where some are found`() {
        val location1 = mockk<Location>()

        every { fhirService.getByIDs(tenant, listOf("fhirId1", "fhirId2")) } returns
            mapOf(
                "fhirId1" to location1,
            )

        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestResourceRequest(listOf(event), fhirService, tenant)

        val key1 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId1"
                every { dateRange } returns null
            }
        val key2 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId2"
                every { dateRange } returns null
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

        val key1 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId1"
                every { dateRange } returns null
            }
        val key2 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId2"
                every { dateRange } returns null
            }

        val resources = request.loadResources(listOf(key1, key2))
        assertEquals(0, resources.size)
    }

    @Test
    fun `backfill logic works`() {
        val observation1 = mockk<Observation>()
        val observation2 = mockk<Observation>()
        val observation3 = mockk<Observation>()
        val observation4 = mockk<Observation>()

        val date1 = OffsetDateTime.of(2023, 10, 24, 12, 0, 0, 0, ZoneOffset.UTC)
        val date2 = OffsetDateTime.of(2023, 10, 25, 12, 0, 0, 0, ZoneOffset.UTC)
        val date3 = OffsetDateTime.of(2023, 10, 26, 12, 0, 0, 0, ZoneOffset.UTC)

        val observationService =
            mockk<ObservationService> {
                every { findObservationsByPatientAndCategory(tenant, listOf("fhirId1"), any(), null, null) } returns
                    listOf(
                        observation1,
                    )
                every {
                    findObservationsByPatientAndCategory(
                        tenant,
                        listOf("fhirId2"),
                        any(),
                        date1.toLocalDate(),
                        date2.toLocalDate(),
                    )
                } returns listOf(observation2)
                every {
                    findObservationsByPatientAndCategory(
                        tenant,
                        listOf("fhirId2"),
                        any(),
                        date2.toLocalDate(),
                        date3.toLocalDate(),
                    )
                } returns listOf(observation4)
                every {
                    findObservationsByPatientAndCategory(
                        tenant,
                        listOf("fhirId3"),
                        any(),
                        date1.toLocalDate(),
                        date2.toLocalDate(),
                    )
                } returns listOf(observation3)
            }

        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestBackfillResourceRequest(listOf(event), observationService, tenant)

        val key1 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId1"
                every { dateRange } returns null
            }
        val key2 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId2"
                every { dateRange } returns Pair(date1, date2)
            }
        val key3 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId3"
                every { dateRange } returns Pair(date1, date2)
            }
        val key4 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId2"
                every { dateRange } returns Pair(date2, date3)
            }

        val resources = request.loadResources(listOf(key1, key2, key3, key4))
        verify(exactly = 1) {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("fhirId1"),
                any(),
                null,
                null,
            )
        }
        verify(exactly = 1) {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("fhirId2"),
                any(),
                date1.toLocalDate(),
                date2.toLocalDate(),
            )
        }
        verify(exactly = 1) {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("fhirId2"),
                any(),
                date2.toLocalDate(),
                date3.toLocalDate(),
            )
        }
        verify(exactly = 1) {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("fhirId3"),
                any(),
                date1.toLocalDate(),
                date2.toLocalDate(),
            )
        }

        assertEquals(4, resources.size)
        assertEquals(listOf(observation1), resources[key1])
        assertEquals(listOf(observation2), resources[key2])
        assertEquals(listOf(observation3), resources[key3])
        assertEquals(listOf(observation4), resources[key4])
    }

    @Test
    fun `backfill logic works when no undated items`() {
        val observation2 = mockk<Observation>()
        val observation3 = mockk<Observation>()
        val observation4 = mockk<Observation>()

        val date1 = OffsetDateTime.of(2023, 10, 24, 12, 0, 0, 0, ZoneOffset.UTC)
        val date2 = OffsetDateTime.of(2023, 10, 25, 12, 0, 0, 0, ZoneOffset.UTC)
        val date3 = OffsetDateTime.of(2023, 10, 26, 12, 0, 0, 0, ZoneOffset.UTC)

        val observationService =
            mockk<ObservationService> {
                every {
                    findObservationsByPatientAndCategory(
                        tenant,
                        listOf("fhirId2"),
                        any(),
                        date1.toLocalDate(),
                        date2.toLocalDate(),
                    )
                } returns listOf(observation2)
                every {
                    findObservationsByPatientAndCategory(
                        tenant,
                        listOf("fhirId2"),
                        any(),
                        date2.toLocalDate(),
                        date3.toLocalDate(),
                    )
                } returns listOf(observation4)
                every {
                    findObservationsByPatientAndCategory(
                        tenant,
                        listOf("fhirId3"),
                        any(),
                        date1.toLocalDate(),
                        date2.toLocalDate(),
                    )
                } returns listOf(observation3)
            }

        val event = mockk<ResourceEvent<InteropResourcePublishV1>>()
        val request = TestBackfillResourceRequest(listOf(event), observationService, tenant)

        val key2 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId2"
                every { dateRange } returns Pair(date1, date2)
            }
        val key3 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId3"
                every { dateRange } returns Pair(date1, date2)
            }
        val key4 =
            mockk<ResourceRequestKey> {
                every { unlocalizedResourceId } returns "fhirId2"
                every { dateRange } returns Pair(date2, date3)
            }

        val resources = request.loadResources(listOf(key2, key3, key4))
        verify(exactly = 1) {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("fhirId2"),
                any(),
                date1.toLocalDate(),
                date2.toLocalDate(),
            )
        }
        verify(exactly = 1) {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("fhirId2"),
                any(),
                date2.toLocalDate(),
                date3.toLocalDate(),
            )
        }
        verify(exactly = 1) {
            observationService.findObservationsByPatientAndCategory(
                tenant,
                listOf("fhirId3"),
                any(),
                date1.toLocalDate(),
                date2.toLocalDate(),
            )
        }

        assertEquals(3, resources.size)
        assertEquals(listOf(observation2), resources[key2])
        assertEquals(listOf(observation3), resources[key3])
        assertEquals(listOf(observation4), resources[key4])
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

    @Test
    fun `returns no minimum registry cache time if no events have one`() {
        val event1 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns null
            }
        val event2 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns null
            }
        val event3 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns null
            }

        val request = TestResourceRequest(listOf(event1, event2, event3), fhirService, tenant)
        assertNull(request.minimumRegistryCacheTime)
    }

    @Test
    fun `returns minimum registry cache time if one event has one`() {
        val offsetDateTime1 = OffsetDateTime.now()
        val event1 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns offsetDateTime1
            }
        val event2 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns null
            }
        val event3 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns null
            }

        val request = TestResourceRequest(listOf(event1, event2, event3), fhirService, tenant)
        assertEquals(offsetDateTime1, request.minimumRegistryCacheTime)
    }

    @Test
    fun `returns latest minimum registry cache time if multiple events have one`() {
        val offsetDateTime1 = OffsetDateTime.of(2022, 7, 24, 11, 24, 0, 0, ZoneOffset.UTC)
        val event1 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns offsetDateTime1
            }
        val offsetDateTime2 = OffsetDateTime.of(2023, 7, 24, 11, 24, 0, 0, ZoneOffset.UTC)
        val event2 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns offsetDateTime2
            }
        val offsetDateTime3 = OffsetDateTime.of(2020, 7, 24, 11, 24, 0, 0, ZoneOffset.UTC)
        val event3 =
            mockk<ResourceEvent<InteropResourcePublishV1>> {
                every { minimumRegistryCacheTime } returns offsetDateTime3
            }

        val request = TestResourceRequest(listOf(event1, event2, event3), fhirService, tenant)
        assertEquals(offsetDateTime2, request.minimumRegistryCacheTime)
    }

    class TestResourceRequest(
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>>,
        override val fhirService: FHIRService<Location>,
        override val tenant: Tenant,
    ) : ResourceRequest<Location, InteropResourcePublishV1>() {
        override val dataTrigger: DataTrigger
            get() = TODO("Not yet implemented")

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<Location>> {
            return fhirService.getByIDs(tenant, requestFhirIds).mapListValues()
        }
    }

    class TestBackfillResourceRequest(
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>>,
        override val fhirService: ObservationService,
        override val tenant: Tenant,
    ) : ResourceRequest<Observation, InteropResourcePublishV1>() {
        override val dataTrigger: DataTrigger
            get() = TODO("Not yet implemented")

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<Observation>> {
            if (requestFhirIds.isEmpty()) {
                throw IllegalStateException("Only call when you have IDs")
            }

            return requestFhirIds.associateWith {
                fhirService.findObservationsByPatientAndCategory(
                    tenant,
                    listOf(it),
                    listOf(FHIRSearchToken(code = "categorY")),
                    startDate?.toLocalDate(),
                    endDate?.toLocalDate(),
                )
            }
        }
    }
}
