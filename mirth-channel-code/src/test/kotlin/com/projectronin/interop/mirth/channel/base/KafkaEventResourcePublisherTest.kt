package com.projectronin.interop.mirth.channel.base

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
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher.ResourceRequestKey
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class KafkaEventResourcePublisherTest {
    private lateinit var tenantService: TenantService
    private lateinit var ehrFactory: EHRFactory
    private lateinit var transformManager: TransformManager
    private lateinit var publishService: PublishService
    private lateinit var roninLocation: RoninLocation
    private lateinit var destination: TestLocationPublish

    private lateinit var tenant: Tenant
    private lateinit var vendorFactory: VendorFactory

    class TestLocationPublish(
        tenantService: TenantService,
        ehrFactory: EHRFactory,
        transformManager: TransformManager,
        publishService: PublishService,
        profileTransformer: RoninLocation,
        override val cacheAndCompareResults: Boolean = false
    ) : KafkaEventResourcePublisher<Location>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
        profileTransformer
    ) {
        var returnedMetadata: Metadata? = null
        var returnedRequestKeys: List<ResourceRequestKey>? = null

        override fun convertEventToRequest(
            serializedEvent: String,
            eventClassName: String,
            vendorFactory: VendorFactory,
            testTenant: Tenant
        ): ResourceLoadRequest<Location> {
            val mockRequest = mockk<ResourceLoadRequest<Location>> {
                every { dataTrigger } returns DataTrigger.NIGHTLY
                every { tenant } returns testTenant
                every { metadata } returns (returnedMetadata ?: mockk())
                every { requestKeys } returns (
                    returnedRequestKeys ?: listOf(
                        ResourceRequestKey(
                            UUID.randomUUID().toString(),
                            ResourceType.Location,
                            testTenant,
                            "123"
                        )
                    )
                    )
            }

            when (serializedEvent) {
                "ehr error" -> {
                    every { mockRequest.loadResources(any()) } throws Exception("EHR is gone")
                }

                "nothing from ehr" -> {
                    every { mockRequest.loadResources(any()) } returns emptyList()
                }

                "long list" -> {
                    every { mockRequest.loadResources(any()) } returns listOf(
                        mockk {
                            every { resourceType } returns "Location"
                            every { id?.value } returns "1"
                        },
                        mockk {
                            every { resourceType } returns "Location"
                            every { id?.value } returns "2"
                        },
                        mockk {
                            every { resourceType } returns "Location"
                            every { id?.value } returns "3"
                        },
                        mockk {
                            every { resourceType } returns "Location"
                            every { id?.value } returns "4"
                        },
                        mockk {
                            every { resourceType } returns "Location"
                            every { id?.value } returns "5"
                        },
                        mockk {
                            every { resourceType } returns "Location"
                            every { id } returns null
                        },
                        mockk {
                            every { resourceType } returns "Location"
                            every { id?.value } returns null
                        }
                    )
                }

                else -> {
                    every { mockRequest.loadResources(any()) } returns listOf(
                        mockk {
                            every { resourceType } returns "Location"
                            every { id?.value } returns "1234"
                        }
                    )
                }
            }
            return mockRequest
        }
    }

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
        destination = TestLocationPublish(tenantService, ehrFactory, transformManager, publishService, roninLocation)
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
    fun `channel works`() {
        val transformed = mockk<Location> {}
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns transformed
        every {
            publishService.publishFHIRResources(
                "tenant",
                listOf(transformed),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"
        val result = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 1 resource(s).", result.message)
    }

    @Test
    fun `fails from ehr`() {
        val result = destination.channelDestinationWriter(
            "tenant",
            "ehr error",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("EHR is gone", result.detailedMessage)
        assertEquals("Failed EHR Call", result.message)
    }

    @Test
    fun `works with nothing from EHR`() {
        val result = destination.channelDestinationWriter(
            "tenant",
            "nothing from ehr",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("No new resources retrieved from EHR.", result.detailedMessage)
        assertEquals("No resources", result.message)
    }

    @Test
    fun `fails transform`() {
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns null
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"
        val result = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Failed to transform 1 resource(s)", result.message)
    }

    @Test
    fun `fails some transform, publishes the rest`() {
        val transformed1 = mockk<Location> { every { id?.value } returns "tenant-1" }
        // we call these the 'the code-cov boys'
        val transformed2 = mockk<Location> { every { id?.value } returns null }
        val transformed3 = mockk<Location> { every { id } returns null }
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns null
        every {
            transformManager.transformResource(
                match { it.id?.value == "1" },
                roninLocation,
                tenant
            )
        } returns transformed1
        every {
            transformManager.transformResource(
                match { it.id?.value == "2" },
                roninLocation,
                tenant
            )
        } returns transformed2
        every {
            transformManager.transformResource(
                match { it.id?.value == "3" },
                roninLocation,
                tenant
            )
        } returns transformed3
        every {
            publishService.publishFHIRResources(
                "tenant",
                listOf(transformed1, transformed2, transformed3),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"
        val result = destination.channelDestinationWriter(
            "tenant",
            "long list",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 3 resource(s).", result.message)
    }

    @Test
    fun `fails publish`() {
        val transformed = mockk<Location> {}
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns transformed
        every {
            publishService.publishFHIRResources(
                "tenant",
                listOf(transformed),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns false
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"
        val result = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Failed to publish 1 resource(s)", result.message)
    }

    @Test
    fun `fails when tenant lookup fails`() {
        every { tenantService.getTenantForMnemonic("nope") } returns null
        assertThrows<IllegalArgumentException> {
            destination.channelDestinationWriter("nope", "", emptyMap(), emptyMap())
        }
    }

    @Test
    fun `fails when didn't pass event name`() {
        val exception = assertThrows<MapVariableMissing> {
            destination.channelDestinationWriter("tenant", "fake event", emptyMap(), emptyMap())
        }
        assertEquals("Missing Event Name", exception.message)
    }

    @Test
    fun `truncate list works`() {
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns null
        val result = destination.channelDestinationWriter(
            "tenant",
            "long list",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("[\"1\",\"2\",\"3\",\"4\",\"5\",null,null]", result.detailedMessage)
        assertEquals("Failed to transform 7 resource(s)", result.message)
    }

    @Test
    fun `LoadEventResourceLoadRequest works`() {
        class TestRequest(sourceEvent: InteropResourceLoadV1) :
            KafkaEventResourcePublisher.LoadEventResourceLoadRequest<Location>(sourceEvent, tenant) {
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
        ) : KafkaEventResourcePublisher.LoadEventResourceLoadRequest<Location>(sourceEvent, tenant)

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
                KafkaEventResourcePublisher.ResourceRequestKey(
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
            KafkaEventResourcePublisher.PublishEventResourceLoadRequest<Location, Patient>(
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

    @Test
    fun `publisher ignores repeat requests`() {
        destination.returnedMetadata = mockk {
            every { runId } returns "run1"
        }
        destination.returnedRequestKeys = listOf(ResourceRequestKey("run1", ResourceType.Patient, tenant, "10"))

        val transformed = mockk<Location> {}
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns transformed
        every {
            publishService.publishFHIRResources(
                "tenant",
                listOf(transformed),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val result1 = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)

        val result2 = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals(
            "All requested resources have already been processed this run: run1:Patient:tenant:10",
            result2.detailedMessage
        )
        assertEquals("Already processed", result2.message)

        verify(exactly = 1) { transformManager.transformResource(any(), roninLocation, tenant) }
        verify(exactly = 1) { publishService.publishFHIRResources(any(), any(), any(), any()) }
    }

    @Test
    fun `publisher ignores already seen responses`() {
        val destination =
            TestLocationPublish(tenantService, ehrFactory, transformManager, publishService, roninLocation, true)
        destination.returnedMetadata = mockk {
            every { runId } returns "run1"
        }
        destination.returnedRequestKeys = listOf(ResourceRequestKey("run1", ResourceType.Patient, tenant, "10"))

        val transformed = mockk<Location> {
            every { resourceType } returns "Location"
            every { id?.value } returns "tenant-12345"
        }
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns transformed
        every {
            publishService.publishFHIRResources(
                "tenant",
                listOf(transformed),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val result1 = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)

        // Change the key, but we're still going to encounter the cached result.
        destination.returnedRequestKeys = listOf(ResourceRequestKey("run1", ResourceType.Patient, tenant, "20"))
        val result2 = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals(
            "No new resources retrieved from EHR. 1 resource(s) were already processed this run.",
            result2.detailedMessage
        )
        assertEquals("No resources", result2.message)

        verify(exactly = 1) { transformManager.transformResource(any(), roninLocation, tenant) }
        verify(exactly = 1) { publishService.publishFHIRResources(any(), any(), any(), any()) }
    }

    @Test
    fun `publisher ignores requests for already seen responses`() {
        val destination =
            TestLocationPublish(tenantService, ehrFactory, transformManager, publishService, roninLocation, true)
        destination.returnedMetadata = mockk {
            every { runId } returns "run1"
        }
        destination.returnedRequestKeys = listOf(ResourceRequestKey("run1", ResourceType.Patient, tenant, "10"))

        val transformed = mockk<Location> {
            every { resourceType } returns "Location"
            every { id?.value } returns "tenant-12345"
        }
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns transformed
        every {
            publishService.publishFHIRResources(
                "tenant",
                listOf(transformed),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val result1 = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)

        destination.returnedRequestKeys = listOf(ResourceRequestKey("run1", ResourceType.Location, tenant, "1234"))
        val result2 = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals(
            "All requested resources have already been processed this run: run1:Location:tenant:1234",
            result2.detailedMessage
        )
        assertEquals("Already processed", result2.message)

        verify(exactly = 1) { transformManager.transformResource(any(), roninLocation, tenant) }
        verify(exactly = 1) { publishService.publishFHIRResources(any(), any(), any(), any()) }
    }

    @Test
    fun `publisher handles inputs that match output`() {
        val destination =
            TestLocationPublish(tenantService, ehrFactory, transformManager, publishService, roninLocation, true)
        destination.returnedMetadata = mockk {
            every { runId } returns "run1"
        }
        destination.returnedRequestKeys = listOf(ResourceRequestKey("run1", ResourceType.Location, tenant, "1234"))

        val transformed = mockk<Location> {
            every { resourceType } returns "Location"
            every { id?.value } returns "tenant-12345"
        }
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns transformed
        every {
            publishService.publishFHIRResources(
                "tenant",
                listOf(transformed),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val result1 = destination.channelDestinationWriter(
            "tenant",
            "fake event",
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)
    }
}
