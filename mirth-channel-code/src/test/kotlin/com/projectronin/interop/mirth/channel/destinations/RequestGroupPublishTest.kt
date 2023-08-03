package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.RequestGroupService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.datatype.primitive.FHIRString
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.CarePlanActivity
import com.projectronin.interop.fhir.r4.resource.RequestGroup
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class RequestGroupPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val requestGroupService = mockk<RequestGroupService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { requestGroupService } returns this@RequestGroupPublishTest.requestGroupService
    }
    private val requestGroupPublish = RequestGroupPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val carePlan1 = CarePlan(
        id = Id("$tenantId-1234"),
        activity = listOf(
            CarePlanActivity(reference = Reference(reference = FHIRString("RequestGroup/$tenantId-1234"))),
            CarePlanActivity(reference = Reference(reference = FHIRString("RequestGroup/$tenantId-5678")))
        )
    )
    private val carePlan2 = CarePlan(
        id = Id("$tenantId-5678"),
        activity = listOf(
            CarePlanActivity(reference = Reference(reference = FHIRString("RequestGroup/$tenantId-9012")))
        )
    )
    private val carePlan3 = CarePlan(
        id = Id("$tenantId-9012"),
        activity = listOf(
            CarePlanActivity(reference = Reference(reference = FHIRString("OtherActivity/$tenantId-3456")))
        )
    )
    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
    }

    @Test
    fun `publish events create a CarePlanPublishRequestGroupRequest`() {
        val publishEvent = mockk<InteropResourcePublishV1> {
            every { resourceJson } returns JacksonManager.objectMapper.writeValueAsString(carePlan1)
            every { metadata } returns this@RequestGroupPublishTest.metadata
        }
        val request = requestGroupPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(RequestGroupPublish.CarePlanPublishRequestGroupRequest::class.java, request)
    }

    @Test
    fun `load events create a LoadRequestGroupRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = requestGroupPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(RequestGroupPublish.LoadRequestGroupRequest::class.java, request)
    }

    @Test
    fun `PatientPublishRequestGroupRequest supports loads resources`() {
        val requestGroup1 = mockk<RequestGroup>()
        val requestGroup2 = mockk<RequestGroup>()
        val requestGroup3 = mockk<RequestGroup>()
        every {
            requestGroupService.getByIDs(
                tenant,
                listOf("1234", "5678", "9012")
            )
        } returns mapOf("1234" to requestGroup1, "5678" to requestGroup2, "9012" to requestGroup3)

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan3),
            metadata = metadata
        )
        val request =
            RequestGroupPublish.CarePlanPublishRequestGroupRequest(
                listOf(event1, event2, event3),
                requestGroupService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.RequestGroup, tenant, "$tenantId-1234")
        assertEquals(listOf(requestGroup1), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.RequestGroup, tenant, "$tenantId-5678")
        assertEquals(listOf(requestGroup2), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.RequestGroup, tenant, "$tenantId-9012")
        assertEquals(listOf(requestGroup3), resourcesByKeys[key3])
    }
}
