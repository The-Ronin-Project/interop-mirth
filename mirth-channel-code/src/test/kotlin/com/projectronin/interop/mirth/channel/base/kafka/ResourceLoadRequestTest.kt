package com.projectronin.interop.mirth.channel.base.kafka

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourceLoadRequestTest {

    private lateinit var tenantService: TenantService
    private lateinit var ehrFactory: EHRFactory
    private lateinit var transformManager: TransformManager
    private lateinit var publishService: PublishService
    private lateinit var roninLocation: RoninLocation
    private lateinit var destination: KafkaEventResourcePublisherTest.TestLocationPublish

    private lateinit var tenant: Tenant
    private lateinit var vendorFactory: VendorFactory

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns "tenant"
        }
        vendorFactory = mockk()
        tenantService = mockk {
            every { getTenantForMnemonic("tenant") } returns tenant
        }
        ehrFactory = mockk {
            every { getVendorFactory(tenant) } returns vendorFactory
        }
        mockkObject(JacksonUtil)
        transformManager = mockk()
        publishService = mockk()
        roninLocation = mockk()
        destination = KafkaEventResourcePublisherTest.TestLocationPublish(
            tenantService,
            ehrFactory,
            transformManager,
            publishService,
            roninLocation
        )
    }

    @Test
    fun `LoadEventResourceLoadRequest works`() {
        class TestRequest(sourceEvent: InteropResourceLoadV1) :
            LoadEventResourceLoadRequest<Location>(sourceEvent, tenant) {
            override val fhirService: FHIRService<Location> = mockk()
        }

        val metadata1 = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event1 = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.adhoc
            every { metadata } returns metadata1
            every { resourceType } returns ResourceType.Location
            every { resourceFHIRId } returns "Location1"
        }
        val metadata2 = mockk<Metadata> {
            every { runId } returns "run124"
        }
        val event2 = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.nightly
            every { metadata } returns metadata2
            every { resourceType } returns ResourceType.Location
            every { resourceFHIRId } returns "Location2"
        }
        val metadata3 = mockk<Metadata> {
            every { runId } returns "run125"
        }
        val event3 = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.backfill
            every { metadata } returns metadata3
            every { resourceType } returns ResourceType.Location
            every { resourceFHIRId } returns "Location3"
        }
        val test1 = TestRequest(event1)
        val test2 = TestRequest(event2)
        assertThrows<IllegalStateException> { TestRequest(event3) }
        assertEquals(DataTrigger.AD_HOC, test1.dataTrigger)
        assertEquals(metadata1, test1.metadata)
        assertEquals(DataTrigger.NIGHTLY, test2.dataTrigger)
        assertEquals(metadata2, test2.metadata)
        assertEquals(event1, test1.sourceEvent)
    }

    @Test
    fun `LoadEventResourceLoadRequest works for default get`() {
        class TestRequest(
            sourceEvent: InteropResourceLoadV1,
            override val fhirService: FHIRService<Location>,
            tenant: Tenant
        ) : LoadEventResourceLoadRequest<Location>(sourceEvent, tenant)

        val mockTenant = mockk<Tenant> {
            every { mnemonic } returns "tenant"
        }
        val mockResource = mockk<Location>()
        val mockService = mockk<FHIRService<Location>> {
            every { getByID(mockTenant, "1") } returns mockResource
        }

        val mockMetadata = mockk<Metadata> {
            every { runId } returns "run123"
        }
        val event = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.adhoc
            every { resourceType } returns ResourceType.Location
            every { resourceFHIRId } returns "1"
            every { metadata } returns mockMetadata
        }
        val test = TestRequest(event, mockService, mockTenant)
        val resources = test.loadResources(
            listOf(
                ResourceRequestKey(
                    "run123",
                    ResourceType.Location,
                    tenant,
                    "1"
                )
            )
        )
        assertEquals(mockResource, resources.first())
    }

    @Test
    fun `PublishEventResourceLoadRequest works`() {
        class TestRequest(sourceEvent: InteropResourcePublishV1) :
            PublishEventResourceLoadRequest<Location, Patient>(
                sourceEvent
            ) {
            override val fhirService: FHIRService<Location> = mockk()
            override val tenant = mockk<Tenant> {
                every { mnemonic } returns "tenant"
            }
            override val requestKeys: List<ResourceRequestKey> =
                listOf(
                    ResourceRequestKey("1", ResourceType.Location, tenant, "1")
                )
            override val sourceResource: Patient = mockk()

            override fun loadResources(requestKeys: List<ResourceRequestKey>): List<Location> {
                throw NotImplementedError("")
            }
        }

        val metadata1 = mockk<Metadata>()
        val event1 = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.adhoc
            every { metadata } returns metadata1
            every { resourceType } returns ResourceType.Location
            every { resourceJson } returns """{"resourceType":"Patient","id":"Patient1"}"""
        }
        val metadata2 = mockk<Metadata>()
        val event2 = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns metadata2
            every { resourceType } returns ResourceType.Location
            every { resourceJson } returns """{"resourceType":"Patient","id":"Patient2"}"""
        }
        val metadata3 = mockk<Metadata>()
        val event3 = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.backfill
            every { metadata } returns metadata3
            every { resourceType } returns ResourceType.Location
            every { resourceJson } returns """{"resourceType":"Patient","id":"Patient3"}"""
        }
        val test1 = TestRequest(event1)
        val test2 = TestRequest(event2)
        assertThrows<IllegalStateException> { TestRequest(event3) }
        assertEquals(DataTrigger.AD_HOC, test1.dataTrigger)
        assertEquals(metadata1, test1.metadata)
        assertEquals(DataTrigger.NIGHTLY, test2.dataTrigger)
        assertEquals(metadata2, test2.metadata)
        assertEquals(event1, test1.sourceEvent)
    }
}
