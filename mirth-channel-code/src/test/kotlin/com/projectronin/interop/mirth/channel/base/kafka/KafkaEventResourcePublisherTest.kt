package com.projectronin.interop.mirth.channel.base.kafka

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager.Companion.objectMapper
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.ConditionService
import com.projectronin.interop.ehr.FHIRService
import com.projectronin.interop.ehr.LocationService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.generators.datatypes.participant
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.appointment
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.resource.Appointment
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Location
import com.projectronin.interop.fhir.r4.resource.Organization
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.resource.RoninLocation
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.fhir.ronin.transform.TransformResponse
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.kafka.model.PublishResourceWrapper
import com.projectronin.interop.mirth.channel.base.DestinationConfiguration
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.PublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishReferenceResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class KafkaEventResourcePublisherTest {
    private lateinit var tenantService: TenantService
    private lateinit var ehrFactory: EHRFactory
    private lateinit var locationService: LocationService
    private lateinit var transformManager: TransformManager
    private lateinit var publishService: PublishService
    private lateinit var roninLocation: RoninLocation
    private lateinit var destination: TestLocationPublish

    private val runId = "run1"
    private val metadata = Metadata(runId = runId, runDateTime = OffsetDateTime.now())

    private val tenantId = "tenant"
    private lateinit var tenant: Tenant
    private lateinit var vendorFactory: VendorFactory

    private val location1234 = mockk<Location> {
        every { resourceType } returns "Location"
        every { id?.value } returns "1234"
    }
    private val location5678 = mockk<Location> {
        every { resourceType } returns "Location"
        every { id?.value } returns "5678"
    }

    private val transformedLocation1234 = mockk<Location> {
        every { resourceType } returns "Location"
        every { id?.value } returns "$tenantId-1234"
    }
    private val transformedLocation5678 = mockk<Location> {
        every { resourceType } returns "Location"
        every { id?.value } returns "$tenantId-5678"
    }

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
        override fun getConfiguration(): DestinationConfiguration {
            TODO("Not yet implemented")
        }

        override fun convertPublishEventsToRequest(
            events: List<InteropResourcePublishV1>,
            vendorFactory: VendorFactory,
            tenant: Tenant
        ): PublishResourceRequest<Location> {
            return object : PublishReferenceResourceRequest<Location>() {
                override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> = events.map {
                    object : PublishResourceEvent<Appointment>(it, Appointment::class) {
                        override val requestKeys: Set<ResourceRequestKey> =
                            sourceResource.participant.asSequence()
                                .filter { it.actor?.decomposedType()?.startsWith("Location") == true }
                                .mapNotNull { it.actor?.decomposedId() }
                                .distinct().map {
                                    ResourceRequestKey(
                                        metadata.runId,
                                        ResourceType.Location,
                                        tenant,
                                        it
                                    )
                                }.toSet()
                    }
                }

                override val fhirService: FHIRService<Location> = vendorFactory.locationService
                override val tenant: Tenant = tenant
            }
        }

        override fun convertLoadEventsToRequest(
            events: List<InteropResourceLoadV1>,
            vendorFactory: VendorFactory,
            tenant: Tenant
        ): LoadResourceRequest<Location> {
            return object : LoadResourceRequest<Location>(events, tenant) {
                override val fhirService: FHIRService<Location> = vendorFactory.locationService
            }
        }
    }

    class TestConditionPublish(
        tenantService: TenantService,
        ehrFactory: EHRFactory,
        transformManager: TransformManager,
        publishService: PublishService,
        profileTransformer: RoninConditions,
        override val cacheAndCompareResults: Boolean = false
    ) : KafkaEventResourcePublisher<Condition>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
        profileTransformer
    ) {
        override fun getConfiguration(): DestinationConfiguration {
            TODO("Not yet implemented")
        }

        override fun convertPublishEventsToRequest(
            events: List<InteropResourcePublishV1>,
            vendorFactory: VendorFactory,
            tenant: Tenant
        ): PublishResourceRequest<Condition> {
            return object : PublishResourceRequest<Condition>() {
                override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> = events.map {
                    object : IdBasedPublishResourceEvent<Patient>(it, tenant, Patient::class) {}
                }

                override val fhirService: ConditionService = vendorFactory.conditionService
                override val tenant: Tenant = tenant

                override fun loadResourcesForIds(
                    requestFhirIds: List<String>,
                    startDate: OffsetDateTime?,
                    endDate: OffsetDateTime?
                ): Map<String, List<Condition>> {
                    return requestFhirIds.associateWith {
                        fhirService.findConditions(
                            tenant,
                            it,
                            "category-code",
                            "clinical-status"
                        )
                    }
                }
            }
        }

        override fun convertLoadEventsToRequest(
            events: List<InteropResourceLoadV1>,
            vendorFactory: VendorFactory,
            tenant: Tenant
        ): LoadResourceRequest<Condition> {
            TODO()
        }
    }

    @BeforeEach
    fun setup() {
        tenant = mockk {
            every { mnemonic } returns tenantId
        }
        locationService = mockk()
        vendorFactory = mockk {
            every { locationService } returns this@KafkaEventResourcePublisherTest.locationService
        }
        tenantService = mockk {
            every { getTenantForMnemonic(tenantId) } returns tenant
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
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        val transformResponse = TransformResponse(transformedLocation1234)
        every { transformManager.transformResource(location1234, roninLocation, tenant) } returns transformResponse
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 1 resource(s).", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `channel works with embedded resources`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        val embedded1 = mockk<Organization>()
        val embedded2 = mockk<Organization>()
        val transformResponse = TransformResponse(transformedLocation1234, listOf(embedded1, embedded2))
        every { transformManager.transformResource(location1234, roninLocation, tenant) } returns transformResponse
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234, listOf(embedded1, embedded2))),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 1 resource(s).", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `fails from ehr`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } throws Exception("EHR is gone")

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("EHR is gone", result.detailedMessage)
        assertEquals("Failed EHR Call", result.message)
        assertNull(result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertNull(result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `works with nothing from EHR`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns emptyMap()

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("No new resources retrieved from EHR.", result.detailedMessage)
        assertEquals("No resources", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertNull(result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertNull(result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `works with nothing from EHR represented as an empty List in the Map`() {
        val roninConditions = mockk<RoninConditions>()
        val conditionService = mockk<ConditionService>()
        every { vendorFactory.conditionService } returns conditionService

        val destination =
            TestConditionPublish(tenantService, ehrFactory, transformManager, publishService, roninConditions)

        val patient = patient {
            id of "$tenantId-1234"
        }
        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = objectMapper.writeValueAsString(patient),
            dataTrigger = InteropResourcePublishV1.DataTrigger.nightly,
            metadata = metadata
        )

        every {
            conditionService.findConditions(
                tenant,
                "1234",
                "category-code",
                "clinical-status"
            )
        } returns emptyList()

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("No new resources retrieved from EHR.", result.detailedMessage)
        assertEquals("No resources", result.message)
        assertEquals("Patient/tenant-1234", result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertNull(result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertNull(result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `fails transform`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every { transformManager.transformResource(location1234, roninLocation, tenant) } returns null
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Failed to transform 1 resource(s)", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertNull(result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(1, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `fails some transform, publishes the rest`() {
        val appointment = appointment {
            id of "1234"
            participant of listOf(
                participant {
                    actor of reference("Location", "1")
                },
                participant {
                    actor of reference("Location", "2")
                },
                participant {
                    actor of reference("Location", "3")
                },
                participant {
                    actor of reference("Location", "4")
                },
                participant {
                    actor of reference("Location", "5")
                }
            )
        }
        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Appointment,
            resourceJson = objectMapper.writeValueAsString(appointment),
            dataTrigger = InteropResourcePublishV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1", "2", "3", "4", "5")) } returns
            mapOf(
                "1" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "1"
                },
                "2" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "2"
                },
                "3" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "3"
                },
                "4" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "4"
                },
                "5" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "5"
                }
            )

        val transformed1 = mockk<Location> {
            every { resourceType } returns "Location"
            every { id?.value } returns "$tenantId-1"
        }
        // we call these the 'the code-cov boys'
        val transformed2 = mockk<Location> {
            every { resourceType } returns "Location"
            every { id?.value } returns null
        }
        val transformed3 = mockk<Location> {
            every { resourceType } returns "Location"
            every { id } returns null
        }
        every { transformManager.transformResource(any(), roninLocation, tenant) } returns null
        every {
            transformManager.transformResource(
                match { it.id?.value == "1" },
                roninLocation,
                tenant
            )
        } returns TransformResponse(transformed1)
        every {
            transformManager.transformResource(
                match { it.id?.value == "2" },
                roninLocation,
                tenant
            )
        } returns TransformResponse(transformed2)
        every {
            transformManager.transformResource(
                match { it.id?.value == "3" },
                roninLocation,
                tenant
            )
        } returns TransformResponse(transformed3)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(
                    PublishResourceWrapper(transformed1),
                    PublishResourceWrapper(transformed2),
                    PublishResourceWrapper(transformed3)
                ),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 3 resource(s).", result.message)
        assertEquals("Appointment/1234", result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(3, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(2, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `fails publish`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns false
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Successfully published 0, but failed to publish 1 resource(s)", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `publish errors with exception`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } throws InterruptedException()
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Successfully published 0, but failed to publish 1 resource(s)", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result.dataMap[MirthKey.FAILURE_COUNT.code])
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
            destination.channelDestinationWriter(tenantId, "fake event", emptyMap(), emptyMap())
        }
        assertEquals("Missing Event Name", exception.message)
    }

    @Test
    fun `truncate list works`() {
        val appointment = appointment {
            id of "1234"
            participant of listOf(
                participant {
                    actor of reference("Location", "1")
                },
                participant {
                    actor of reference("Location", "2")
                },
                participant {
                    actor of reference("Location", "3")
                },
                participant {
                    actor of reference("Location", "4")
                },
                participant {
                    actor of reference("Location", "5")
                },
                participant {
                    actor of reference("Location", "6")
                },
                participant {
                    actor of reference("Location", "7")
                }
            )
        }
        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Appointment,
            resourceJson = objectMapper.writeValueAsString(appointment),
            dataTrigger = InteropResourcePublishV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1", "2", "3", "4", "5", "6", "7")) } returns
            mapOf(
                "1" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "1"
                },
                "2" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "2"
                },
                "3" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "3"
                },
                "4" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "4"
                },
                "5" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "5"
                },
                "6" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "6"
                },
                "7" to mockk {
                    every { resourceType } returns "Location"
                    every { id?.value } returns "7"
                }
            )

        every { transformManager.transformResource(any(), roninLocation, tenant) } returns null

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"]", result.detailedMessage)
        assertEquals("Failed to transform 7 resource(s)", result.message)
        assertEquals("Appointment/1234", result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(7, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `publisher ignores repeat requests`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result1 = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)
        assertNull(result1.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(0, result1.dataMap[MirthKey.FAILURE_COUNT.code])
        assertEquals(1, result1.dataMap[MirthKey.RESOURCE_COUNT.code])

        val result2 = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals(
            "All requested resources have already been processed this run: $runId:Location:null:$tenantId:1234",
            result2.detailedMessage
        )
        assertEquals("Already processed", result2.message)
        assertNull(result2.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertNull(result2.dataMap[MirthKey.FAILURE_COUNT.code])
        assertNull(result2.dataMap[MirthKey.RESOURCE_COUNT.code])

        verify(exactly = 1) { transformManager.transformResource(any(), roninLocation, tenant) }
        verify(exactly = 1) { publishService.publishResourceWrappers(any(), any(), any(), any()) }
    }

    @Test
    fun `publisher ignores already seen responses`() {
        val destination =
            TestLocationPublish(tenantService, ehrFactory, transformManager, publishService, roninLocation, true)

        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message1 = objectMapper.writeValueAsString(listOf(event1))
        val result1 = destination.channelDestinationWriter(
            tenantId,
            message1,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)
        assertNull(result1.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result1.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result1.dataMap[MirthKey.FAILURE_COUNT.code])

        val event2 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1235",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        // Change the key, but we're still going to encounter the cached result.
        every { locationService.getByIDs(tenant, listOf("1235")) } returns mapOf("1235" to location1234)

        val message2 = objectMapper.writeValueAsString(listOf(event2))
        val result2 = destination.channelDestinationWriter(
            tenantId,
            message2,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals(
            "No new resources retrieved from EHR. 1 resource(s) were already processed this run.",
            result2.detailedMessage
        )
        assertEquals("No resources", result2.message)
        assertNull(result2.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertNull(result2.dataMap[MirthKey.FAILURE_COUNT.code])
        assertNull(result2.dataMap[MirthKey.RESOURCE_COUNT.code])

        verify(exactly = 1) { transformManager.transformResource(any(), roninLocation, tenant) }
        verify(exactly = 1) { publishService.publishResourceWrappers(any(), any(), any(), any()) }
    }

    @Test
    fun `publisher ignores requests for already seen responses`() {
        val destination =
            TestLocationPublish(tenantService, ehrFactory, transformManager, publishService, roninLocation, true)

        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1233",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1233")) } returns mapOf("1233" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message1 = objectMapper.writeValueAsString(listOf(event1))
        val result1 = destination.channelDestinationWriter(
            tenantId,
            message1,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)
        assertNull(result1.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result1.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result1.dataMap[MirthKey.FAILURE_COUNT.code])

        val event2 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        val message2 = objectMapper.writeValueAsString(listOf(event2))
        val result2 = destination.channelDestinationWriter(
            tenantId,
            message2,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals(
            "All requested resources have already been processed this run: run1:Location:null:tenant:1234",
            result2.detailedMessage
        )
        assertEquals("Already processed", result2.message)
        assertNull(result2.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertNull(result2.dataMap[MirthKey.FAILURE_COUNT.code])
        assertNull(result2.dataMap[MirthKey.RESOURCE_COUNT.code])

        verify(exactly = 1) { transformManager.transformResource(any(), roninLocation, tenant) }
        verify(exactly = 1) { publishService.publishResourceWrappers(any(), any(), any(), any()) }
    }

    @Test
    fun `publisher handles inputs that match output`() {
        val destination =
            TestLocationPublish(tenantService, ehrFactory, transformManager, publishService, roninLocation, true)

        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result1 = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Published 1 resource(s).", result1.message)
        assertNull(result1.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(0, result1.dataMap[MirthKey.FAILURE_COUNT.code])
        assertEquals(1, result1.dataMap[MirthKey.RESOURCE_COUNT.code])
    }

    @Test
    fun `publisher works for repeat load requests if first fails`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns false andThen true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message1 = objectMapper.writeValueAsString(listOf(event1))
        val result1 = destination.channelDestinationWriter(
            tenantId,
            message1,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Successfully published 0, but failed to publish 1 resource(s)", result1.message)
        assertNull(result1.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result1.dataMap[MirthKey.FAILURE_COUNT.code])

        val result2 = destination.channelDestinationWriter(
            tenantId,
            message1,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals("we made it", result2.detailedMessage)
        assertEquals("Published 1 resource(s).", result2.message)
        assertNull(result2.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result2.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result2.dataMap[MirthKey.FAILURE_COUNT.code])

        verify(exactly = 2) { transformManager.transformResource(any(), roninLocation, tenant) }
        verify(exactly = 2) { publishService.publishResourceWrappers(any(), any(), any(), any()) }
    }

    @Test
    fun `publisher works for repeat publish requests if first fails`() {
        val roninConditions = mockk<RoninConditions>()
        val conditionService = mockk<ConditionService>()
        every { vendorFactory.conditionService } returns conditionService

        val destination =
            TestConditionPublish(tenantService, ehrFactory, transformManager, publishService, roninConditions)

        val patient = patient {
            id of "$tenantId-1234"
        }
        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = objectMapper.writeValueAsString(patient),
            dataTrigger = InteropResourcePublishV1.DataTrigger.nightly,
            metadata = metadata
        )

        val condition1 = mockk<Condition> {
            every { resourceType } returns "Condition"
            every { id?.value } returns "1"
        }
        val condition2 = mockk<Condition> {
            every { resourceType } returns "Condition"
            every { id?.value } returns "2"
        }
        every {
            conditionService.findConditions(
                tenant,
                "1234",
                "category-code",
                "clinical-status"
            )
        } returns listOf(condition1, condition2)

        val transformed1 = mockk<Condition> {
            every { resourceType } returns "Condition"
            every { id?.value } returns "$tenantId-1"
        }
        val transformed2 = mockk<Condition> {
            every { resourceType } returns "Condition"
            every { id?.value } returns "$tenantId-2"
        }
        every {
            transformManager.transformResource(condition1, roninConditions, tenant)
        } returns TransformResponse(transformed1)
        every {
            transformManager.transformResource(condition2, roninConditions, tenant)
        } returns TransformResponse(transformed2)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformed1), PublishResourceWrapper(transformed2)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns false andThen true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message1 = objectMapper.writeValueAsString(listOf(event1))
        val result1 = destination.channelDestinationWriter(
            tenantId,
            message1,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result1.status)
        assertEquals("we made it", result1.detailedMessage)
        assertEquals("Successfully published 0, but failed to publish 2 resource(s)", result1.message)
        assertEquals("Patient/$tenantId-1234", result1.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(2, result1.dataMap[MirthKey.FAILURE_COUNT.code])

        val result2 = destination.channelDestinationWriter(
            tenantId,
            message1,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result2.status)
        assertEquals("we made it", result2.detailedMessage)
        assertEquals("Published 2 resource(s).", result2.message)
        assertEquals("Patient/$tenantId-1234", result2.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(2, result2.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result2.dataMap[MirthKey.FAILURE_COUNT.code])

        verify(exactly = 4) { transformManager.transformResource(any(), roninConditions, tenant) }
        verify(exactly = 2) { publishService.publishResourceWrappers(any(), any(), any(), any()) }
    }

    @Test
    fun `handles multiple events where all succeed`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val event2 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-5678",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234", "5678")) } returns mapOf(
            "1234" to location1234,
            "5678" to location5678
        )

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true

        every {
            transformManager.transformResource(location5678, roninLocation, tenant)
        } returns TransformResponse(transformedLocation5678)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation5678)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true

        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1, event2))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 2 resource(s).", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(2, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `handles multiple events where some fail to publish and some succeed`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val event2 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-5678",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234", "5678")) } returns mapOf(
            "1234" to location1234,
            "5678" to location5678
        )

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true

        every {
            transformManager.transformResource(location5678, roninLocation, tenant)
        } returns TransformResponse(transformedLocation5678)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation5678)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns false

        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1, event2))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Successfully published 1, but failed to publish 1 resource(s)", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(1, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `handles multiple events where all fail publishing`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )
        val event2 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-5678",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata
        )

        every { locationService.getByIDs(tenant, listOf("1234", "5678")) } returns mapOf(
            "1234" to location1234,
            "5678" to location5678
        )

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns false

        every {
            transformManager.transformResource(location5678, roninLocation, tenant)
        } returns TransformResponse(transformedLocation5678)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation5678)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns false

        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1, event2))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Successfully published 0, but failed to publish 2 resource(s)", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(0, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(2, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `supports events that do not process downstream references`() {
        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata,
            flowOptions = InteropResourceLoadV1.FlowOptions(
                disableDownstreamResources = true
            )
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(location1234, roninLocation, tenant)
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                null
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 1 resource(s).", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `supports events that provide a minimum registry time`() {
        val registryOffsetDateTime = OffsetDateTime.of(2023, 7, 24, 11, 14, 0, 0, ZoneOffset.UTC)
        val registryLocalDateTime = LocalDateTime.of(2023, 7, 24, 11, 14, 0)

        val event1 = InteropResourceLoadV1(
            tenantId = tenantId,
            resourceFHIRId = "$tenantId-1234",
            resourceType = ResourceType.Location,
            dataTrigger = InteropResourceLoadV1.DataTrigger.nightly,
            metadata = metadata,
            flowOptions = InteropResourceLoadV1.FlowOptions(
                normalizationRegistryMinimumTime = registryOffsetDateTime
            )
        )

        every { locationService.getByIDs(tenant, listOf("1234")) } returns mapOf("1234" to location1234)

        every {
            transformManager.transformResource(
                location1234,
                roninLocation,
                tenant,
                registryLocalDateTime
            )
        } returns TransformResponse(transformedLocation1234)
        every {
            publishService.publishResourceWrappers(
                tenantId,
                listOf(PublishResourceWrapper(transformedLocation1234)),
                any(),
                DataTrigger.NIGHTLY
            )
        } returns true
        every { JacksonUtil.writeJsonValue(any()) } returns "we made it"

        val message = objectMapper.writeValueAsString(listOf(event1))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourceLoadV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("we made it", result.detailedMessage)
        assertEquals("Published 1 resource(s).", result.message)
        assertNull(result.dataMap[MirthKey.EVENT_METADATA_SOURCE.code])
        assertEquals(1, result.dataMap[MirthKey.RESOURCE_COUNT.code])
        assertEquals(0, result.dataMap[MirthKey.FAILURE_COUNT.code])
    }

    @Test
    fun `publisher catches empty requests`() {
        val appointment = appointment {
            id of "1234"
            participant of emptyList()
        }
        val event = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Appointment,
            resourceJson = objectMapper.writeValueAsString(appointment),
            dataTrigger = InteropResourcePublishV1.DataTrigger.nightly,
            metadata = metadata
        )
        val message = objectMapper.writeValueAsString(listOf(event))
        val result = destination.channelDestinationWriter(
            tenantId,
            message,
            mapOf(MirthKey.KAFKA_EVENT.code to InteropResourcePublishV1::class.simpleName!!),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals(
            "No request keys exist prior to checking the cache",
            result.detailedMessage
        )
        assertEquals("No request keys exist prior to checking the cache", result.message)
        assertNull(result.dataMap[MirthKey.FAILURE_COUNT.code])
        assertNull(result.dataMap[MirthKey.RESOURCE_COUNT.code])
    }
}
