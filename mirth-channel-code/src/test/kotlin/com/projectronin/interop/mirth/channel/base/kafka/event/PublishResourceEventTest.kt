package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class PublishResourceEventTest {
    private val metadata = Metadata(runId = "run", runDateTime = OffsetDateTime.now())
    private val location = Location(id = Id("tenant-1234"))
    private val sourceEvent =
        InteropResourcePublishV1(
            tenantId = "tenant",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourcePublishV1.DataTrigger.nightly,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(location),
            metadata = metadata,
        )
    private val publishResourceEvent =
        object : PublishResourceEvent<Location>(sourceEvent, Location::class) {
            override val requestKeys: Set<ResourceRequestKey>
                get() = TODO("Not yet implemented")
        }

    @Test
    fun `returns metadata`() {
        assertEquals(metadata, publishResourceEvent.metadata)
    }

    @Test
    fun `processes downstream references`() {
        assertTrue(publishResourceEvent.processDownstreamReferences)
    }

    @Test
    fun `never returns a minimum registry cache time`() {
        assertNull(publishResourceEvent.minimumRegistryCacheTime)
    }

    @Test
    fun `returns updated metadata when no upstream references existed`() {
        val updatedMetadata = publishResourceEvent.getUpdatedMetadata()
        assertEquals(metadata.runId, updatedMetadata.runId)
        assertEquals(metadata.runDateTime, updatedMetadata.runDateTime)

        val sourceReference = Metadata.UpstreamReference(ResourceType.Location, "tenant-1234")
        assertEquals(listOf(sourceReference), updatedMetadata.upstreamReferences)
    }

    @Test
    fun `returns updated metadata when upstream references existed`() {
        val patientReference = Metadata.UpstreamReference(ResourceType.Patient, "tenant-2468")
        val appointmentReference = Metadata.UpstreamReference(ResourceType.Appointment, "tenant-1357")
        val testMetadata = metadata.copy(upstreamReferences = listOf(patientReference, appointmentReference))
        val testSourceEvent = sourceEvent.copy(metadata = testMetadata)

        val publishResourceEvent =
            object : PublishResourceEvent<Location>(testSourceEvent, Location::class) {
                override val requestKeys: Set<ResourceRequestKey>
                    get() = TODO("Not yet implemented")
            }

        val updatedMetadata = publishResourceEvent.getUpdatedMetadata()
        assertEquals(testMetadata.runId, updatedMetadata.runId)
        assertEquals(testMetadata.runDateTime, updatedMetadata.runDateTime)

        val sourceReference = Metadata.UpstreamReference(ResourceType.Location, "tenant-1234")
        assertEquals(
            listOf(patientReference, appointmentReference, sourceReference),
            updatedMetadata.upstreamReferences,
        )
    }

    @Test
    fun `returns source reference`() {
        val sourceReference = publishResourceEvent.getSourceReference()
        assertEquals(ResourceType.Location, sourceReference.resourceType)
        assertEquals("tenant-1234", sourceReference.id)
    }
}
