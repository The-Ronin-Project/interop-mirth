package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PublishReferenceResourceRequestTest {
    private val fhirService = mockk<FHIRService<Location>>()
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns "tenant"
    }

    @Test
    fun `loadResourcesForIds requests from fhirService`() {
        val location = Location(id = Id("12345"))
        every { fhirService.getByIDs(tenant, listOf("12345")) } returns mapOf("12345" to location)

        val publishEvent = mockk<ResourceEvent<InteropResourcePublishV1>>()

        val request = TestPublishReferenceResourceRequest(listOf(publishEvent), fhirService, tenant)
        val resources = request.loadResourcesForIds(listOf("12345"), endDate = null)
        Assertions.assertEquals(1, resources.size)
        Assertions.assertEquals(listOf(location), resources["12345"])
    }

    class TestPublishReferenceResourceRequest(
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>>,
        override val fhirService: FHIRService<Location>,
        override val tenant: Tenant
    ) : PublishReferenceResourceRequest<Location>()
}
