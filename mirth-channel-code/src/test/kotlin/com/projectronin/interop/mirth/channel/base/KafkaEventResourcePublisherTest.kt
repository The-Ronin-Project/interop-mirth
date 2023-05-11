package com.projectronin.interop.mirth.channel.base

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.kafka.model.DataTrigger
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        profileTransformer: RoninLocation
    ) : KafkaEventResourcePublisher<Location>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
        profileTransformer
    ) {

        override fun convertEventToRequest(
            serializedEvent: String,
            eventClassName: String,
            vendorFactory: VendorFactory,
            tenant: Tenant
        ): ResourceLoadRequest<Location> {
            val mockRequest = mockk<ResourceLoadRequest<Location>> {
                every { dataTrigger } returns DataTrigger.NIGHTLY
                every { metadata } returns mockk()
            }

            when (serializedEvent) {
                "ehr error" -> {
                    every { mockRequest.loadResources() } throws Exception("EHR is gone")
                }

                "nothing from ehr" -> {
                    every { mockRequest.loadResources() } returns emptyList()
                }

                "long list" -> {
                    every { mockRequest.loadResources() } returns listOf(
                        mockk<Location> { every { id?.value } returns "1" },
                        mockk<Location> { every { id?.value } returns "2" },
                        mockk<Location> { every { id?.value } returns "3" },
                        mockk<Location> { every { id?.value } returns "4" },
                        mockk<Location> { every { id?.value } returns "5" },
                        mockk<Location> { every { id } returns null },
                        mockk<Location> { every { id?.value } returns null }
                    )
                }

                else -> {
                    every { mockRequest.loadResources() } returns listOf(
                        mockk()
                    )
                }
            }
            return mockRequest
        }
    }

    @BeforeEach
    fun setup() {
        tenant = mockk()
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
        assertEquals("Published 1 resource(s)", result.message)
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
        assertEquals("No resources retrieved from EHR", result.detailedMessage)
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
        assertEquals("Published 3 resource(s)", result.message)
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
            KafkaEventResourcePublisher.LoadEventResourceLoadRequest<Location>(sourceEvent) {
            override val fhirService: FHIRService<Location> = mockk()
            override val tenant = mockk<Tenant>()
        }

        val metadata1 = mockk<Metadata>()
        val event1 = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.adhoc
            every { metadata } returns metadata1
        }
        val metadata2 = mockk<Metadata>()
        val event2 = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.nightly
            every { metadata } returns metadata2
        }
        val metadata3 = mockk<Metadata>()
        val event3 = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.backfill
            every { metadata } returns metadata3
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
            override val tenant: Tenant
        ) : KafkaEventResourcePublisher.LoadEventResourceLoadRequest<Location>(sourceEvent)

        val mockTenant = mockk<Tenant>()
        val mockResource = mockk<Location>()
        val mockService = mockk<FHIRService<Location>> {
            every { getByID(mockTenant, "1") } returns mockResource
        }

        val mockMetadata = mockk<Metadata>()
        val event = mockk<InteropResourceLoadV1> {
            every { dataTrigger } returns InteropResourceLoadV1.DataTrigger.adhoc
            every { resourceFHIRId } returns "1"
            every { metadata } returns mockMetadata
        }
        val test = TestRequest(event, mockService, mockTenant)
        val resources = test.loadResources()
        assertEquals(mockResource, resources.first())
    }

    @Test
    fun `PublishEventResourceLoadRequest works`() {
        class TestRequest(sourceEvent: InteropResourcePublishV1) :
            KafkaEventResourcePublisher.PublishEventResourceLoadRequest<Location>(sourceEvent) {
            override val fhirService: FHIRService<Location> = mockk()
            override val tenant = mockk<Tenant>()
            override fun loadResources(): List<Location> {
                throw NotImplementedError("")
            }
        }

        val metadata1 = mockk<Metadata>()
        val event1 = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.adhoc
            every { metadata } returns metadata1
        }
        val metadata2 = mockk<Metadata>()
        val event2 = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.nightly
            every { metadata } returns metadata2
        }
        val metadata3 = mockk<Metadata>()
        val event3 = mockk<InteropResourcePublishV1> {
            every { dataTrigger } returns InteropResourcePublishV1.DataTrigger.backfill
            every { metadata } returns metadata3
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
