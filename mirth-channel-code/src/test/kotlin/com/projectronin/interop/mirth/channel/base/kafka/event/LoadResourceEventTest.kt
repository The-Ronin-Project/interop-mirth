package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class LoadResourceEventTest {
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns "tenant"
    }
    private val metadata = Metadata(runId = "run", runDateTime = OffsetDateTime.now())
    private val sourceEvent = InteropResourceLoadV1(
        tenantId = "tenant",
        resourceFHIRId = "12345",
        resourceType = ResourceType.Location,
        dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
        metadata = metadata
    )
    private val loadResourceEvent = LoadResourceEvent(sourceEvent, tenant)

    @Test
    fun `returns metadata`() {
        assertEquals(metadata, loadResourceEvent.metadata)
    }

    @Test
    fun `processes downstream references if no flow options are set`() {
        assertTrue(loadResourceEvent.processDownstreamReferences)
    }

    @Test
    fun `processes downstream references if value is not set on flow options`() {
        val sourceEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata,
            flowOptions = InteropResourceLoadV1.FlowOptions(
                disableDownstreamResources = null
            )
        )
        val loadResourceEvent = LoadResourceEvent(sourceEvent, tenant)
        assertTrue(loadResourceEvent.processDownstreamReferences)
    }

    @Test
    fun `processes downstream references if value is false on flow options`() {
        val sourceEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata,
            flowOptions = InteropResourceLoadV1.FlowOptions(
                disableDownstreamResources = false
            )
        )
        val loadResourceEvent = LoadResourceEvent(sourceEvent, tenant)
        assertTrue(loadResourceEvent.processDownstreamReferences)
    }

    @Test
    fun `does not process downstream references if value is true on flow options`() {
        val sourceEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata,
            flowOptions = InteropResourceLoadV1.FlowOptions(
                disableDownstreamResources = true
            )
        )
        val loadResourceEvent = LoadResourceEvent(sourceEvent, tenant)
        assertFalse(loadResourceEvent.processDownstreamReferences)
    }

    @Test
    fun `returns no minimum registry cache time if no flow options are set`() {
        assertNull(loadResourceEvent.minimumRegistryCacheTime)
    }

    @Test
    fun `returns no minimum registry cache time if value is not set on flow options`() {
        val sourceEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata,
            flowOptions = InteropResourceLoadV1.FlowOptions(
                normalizationRegistryMinimumTime = null
            )
        )
        val loadResourceEvent = LoadResourceEvent(sourceEvent, tenant)
        assertNull(loadResourceEvent.minimumRegistryCacheTime)
    }

    @Test
    fun `returns minimum registry cache time if value is set on flow options`() {
        val offsetDateTime = OffsetDateTime.now()
        val sourceEvent = InteropResourceLoadV1(
            tenantId = "tenant",
            resourceFHIRId = "12345",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata,
            flowOptions = InteropResourceLoadV1.FlowOptions(
                normalizationRegistryMinimumTime = offsetDateTime
            )
        )
        val loadResourceEvent = LoadResourceEvent(sourceEvent, tenant)
        assertEquals(offsetDateTime, loadResourceEvent.minimumRegistryCacheTime)
    }

    @Test
    fun `returns requestKeys containing load request`() {
        val requestKeys = loadResourceEvent.requestKeys
        assertEquals(1, requestKeys.size)

        val key1 = requestKeys.first()
        assertEquals("run", key1.runId)
        assertEquals(ResourceType.Location, key1.resourceType)
        assertEquals(tenant, key1.tenant)
        assertEquals("12345", key1.resourceId)
    }

    @Test
    fun `returns updatedMetadata matching metadata`() {
        assertEquals(metadata, loadResourceEvent.getUpdatedMetadata())
    }

    @Test
    fun `returns null sourceReference`() {
        assertNull(loadResourceEvent.getSourceReference())
    }
}
