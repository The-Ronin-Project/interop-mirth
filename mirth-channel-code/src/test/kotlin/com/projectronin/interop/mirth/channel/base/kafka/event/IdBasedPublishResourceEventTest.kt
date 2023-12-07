package com.projectronin.interop.mirth.channel.base.kafka.event

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class IdBasedPublishResourceEventTest {
    @Test
    fun `creates the requestKeys off the source ID`() {
        val location = Location(id = Id("tenant-1234"))
        val metadata = Metadata(runId = "run", runDateTime = OffsetDateTime.now())
        val event =
            InteropResourcePublishV1(
                tenantId = "tenant",
                resourceType = ResourceType.Location,
                dataTrigger = InteropResourcePublishV1.DataTrigger.nightly,
                resourceJson = JacksonManager.objectMapper.writeValueAsString(location),
                metadata = metadata,
            )
        val tenant =
            mockk<Tenant> {
                every { mnemonic } returns "tenant"
            }

        val resourceEvent = object : IdBasedPublishResourceEvent<Location>(event, tenant, Location::class) {}
        val requestKeys = resourceEvent.requestKeys
        assertEquals(1, requestKeys.size)

        val key1 = requestKeys.first()
        assertEquals("run", key1.runId)
        assertEquals(ResourceType.Location, key1.resourceType)
        assertEquals(tenant, key1.tenant)
        assertEquals("tenant-1234", key1.resourceId)
    }
}
