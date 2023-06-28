package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.RequestGroup
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RequestGroupPublishTest {
    lateinit var tenant: Tenant
    lateinit var destination: RequestGroupPublish

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        destination = RequestGroupPublish(mockk(), mockk(), mockk(), mockk(), mockk())
        mockkObject(JacksonUtil)
    }

    @AfterEach
    fun unmockk() {
        unmockkAll()
    }

    @Test
    fun `channel creation works`() {
        assertNotNull(destination)
    }

    @Test
    fun `fails on unknown request`() {
        assertThrows<IllegalStateException> {
            destination.convertEventToRequest("boo", "", mockk(), mockk())
        }
    }

    @Test
    fun `works for load events`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourceLoadV1(
            "tenant",
            "id",
            ResourceType.RequestGroup,
            InteropResourceLoadV1.DataTrigger.adhoc,
            metadata
        )
        val mockRequestGroup = mockk<RequestGroup>()
        every { JacksonUtil.readJsonObject("boo", InteropResourceLoadV1::class) } returns event
        val mockVendorFactory = mockk<VendorFactory> {
            every { requestGroupService.getByID(tenant, "id") } returns mockRequestGroup
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourceLoadV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(
            KafkaEventResourcePublisher.ResourceRequestKey(
                "run123",
                ResourceType.RequestGroup,
                tenant,
                "id"
            )
        )
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockRequestGroup, results.first())
    }

    @Test
    fun `works for publish events`() {
        val metadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = InteropResourcePublishV1(
            "tenant",
            ResourceType.CarePlan,
            InteropResourcePublishV1.DataTrigger.adhoc,
            "{}",
            metadata
        )
        val mockCarePlan = mockk<CarePlan> {
            every { id?.value } returns "123"
            every { activity } returns listOf(
                mockk {
                    every { reference } returns mockk {
                        every { decomposedId() } returns "tenant-456"
                        every { decomposedType() } returns "RequestGroup"
                    }
                }
            )
        }
        val mockRequestGroup = mockk<RequestGroup> {
        }
        every { JacksonUtil.readJsonObject("boo", InteropResourcePublishV1::class) } returns event
        every { JacksonUtil.readJsonObject("{}", CarePlan::class) } returns mockCarePlan
        val mockVendorFactory = mockk<VendorFactory> {
            every { requestGroupService.getRequestGroupByFHIRId(tenant, listOf("456")) } returns
                mapOf("456" to mockRequestGroup)
        }
        val request = destination.convertEventToRequest(
            "boo",
            InteropResourcePublishV1::class.simpleName!!,
            mockVendorFactory,
            tenant
        )

        val requestKeys = listOf(
            KafkaEventResourcePublisher.ResourceRequestKey(
                "run123",
                ResourceType.RequestGroup,
                tenant,
                "tenant-456"
            )
        )
        assertEquals(requestKeys, request.requestKeys)

        val results = request.loadResources(requestKeys)
        assertEquals(mockRequestGroup, results.first())
    }
}
