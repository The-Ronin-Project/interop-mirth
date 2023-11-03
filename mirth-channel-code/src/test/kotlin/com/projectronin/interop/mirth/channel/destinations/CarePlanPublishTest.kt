package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.ehr.CarePlanService
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.generators.datatypes.DynamicValues
import com.projectronin.interop.fhir.generators.datatypes.extension
import com.projectronin.interop.fhir.generators.datatypes.reference
import com.projectronin.interop.fhir.generators.resources.carePlan
import com.projectronin.interop.fhir.generators.resources.carePlanActivity
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime

class CarePlanPublishTest {
    private val tenantId = "tenant"
    private val tenant = mockk<Tenant> {
        every { mnemonic } returns tenantId
    }
    private val carePlanService = mockk<CarePlanService>()
    private val vendorFactory = mockk<VendorFactory> {
        every { carePlanService } returns this@CarePlanPublishTest.carePlanService
    }
    private val carePlanPublish = CarePlanPublish(mockk(), mockk(), mockk(), mockk(), mockk())

    private val patient1 = Patient(id = Id("$tenantId-1234"))
    private val patient2 = Patient(id = Id("$tenantId-5678"))
    private val patient3 = Patient(id = Id("$tenantId-9012"))
    private val carePlan = CarePlan(id = Id("$tenantId-3456"))
    private val metadata = mockk<Metadata>(relaxed = true) {
        every { runId } returns "run"
        every { backfillRequest } returns null
    }

    @Test
    fun `publish events create a PatientPublishCarePlanRequest`() {
        val publishEvent = mockk<InteropResourcePublishV1>() {
            every { resourceType } returns ResourceType.Patient
        }
        val request = carePlanPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(CarePlanPublish.PatientPublishCarePlanRequest::class.java, request)
    }

    @Test
    fun `load events create a LoadCarePlanRequest`() {
        val loadEvent = mockk<InteropResourceLoadV1>(relaxed = true)
        val request = carePlanPublish.convertLoadEventsToRequest(listOf(loadEvent), vendorFactory, tenant)
        assertInstanceOf(CarePlanPublish.LoadCarePlanRequest::class.java, request)
    }

    @Test
    fun `PatientPublishCarePlanRequest supports loads resources`() {
        val carePlan1 = mockk<CarePlan>()
        val carePlan2 = mockk<CarePlan>()
        val carePlan3 = mockk<CarePlan>()
        val startDate = OffsetDateTime.now()
        val endDate = OffsetDateTime.now()
        every { carePlanService.findPatientCarePlans(tenant, "1234", any(), any()) } returns listOf(
            carePlan1,
            carePlan2
        )
        every { carePlanService.findPatientCarePlans(tenant, "5678", any(), any()) } returns listOf(carePlan3)
        every { carePlanService.findPatientCarePlans(tenant, "9012", startDate.toLocalDate(), endDate.toLocalDate()) } returns emptyList()

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient1),
            metadata = metadata
        )
        val event2 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient2),
            metadata = metadata
        )
        val event3 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.Patient,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(patient3),
            metadata = Metadata(
                runId = "run",
                runDateTime = OffsetDateTime.now(),
                upstreamReferences = null,
                backfillRequest = Metadata.BackfillRequest(
                    backfillId = "123",
                    backfillStartDate = startDate,
                    backfillEndDate = endDate
                )
            )
        )
        val request =
            CarePlanPublish.PatientPublishCarePlanRequest(
                listOf(event1, event2, event3),
                carePlanService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(3, resourcesByKeys.size)

        val key1 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-1234")
        assertEquals(listOf(carePlan1, carePlan2), resourcesByKeys[key1])

        val key2 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-5678")
        assertEquals(listOf(carePlan3), resourcesByKeys[key2])

        val key3 = ResourceRequestKey("run", ResourceType.Patient, tenant, "$tenantId-9012", Pair(startDate, endDate))
        assertEquals(emptyList<CarePlan>(), resourcesByKeys[key3])
    }

    @Test
    fun `publish events create a PatientPublishCarePlanRequest for careplan publish events`() {
        val publishEvent = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan),
            metadata = metadata
        )
        val request = carePlanPublish.convertPublishEventsToRequest(listOf(publishEvent), vendorFactory, tenant)
        assertInstanceOf(CarePlanPublish.CarePlanPublishCarePlanRequest::class.java, request)
    }

    @Test
    fun `publish events throw exception for unsupported publish events`() {
        val publishEvent = mockk<InteropResourcePublishV1> {
            every { resourceType } returns ResourceType.Practitioner
        }
        val exception = assertThrows<IllegalStateException> {
            carePlanPublish.convertPublishEventsToRequest(
                listOf(publishEvent),
                vendorFactory,
                tenant
            )
        }
        assertEquals(
            "Received resource type (Practitioner) that cannot be used to load care plans",
            exception.message
        )
    }

    @Test
    fun `published care plan with activity cycle defined loads child CarePlan`() {
        val carePlan1 = carePlan {
            subject of reference("Patient", "5678")
            activity of listOf(
                carePlanActivity {
                    extension of listOf(
                        extension {
                            url of Uri("http://open.epic.com/FHIR/StructureDefinition/extension/cycle")
                            value of DynamicValues.reference(
                                reference("CarePlan", "ChildPlanId")
                            )
                        }
                    )
                }
            )
        }

        val carePlan2 = carePlan {
            id of Id("ChildPlanId")
            subject of reference("Patient", "5678")
        }

        every { carePlanService.getByIDs(tenant, listOf("ChildPlanId")) } returns mapOf("ChildPlanId" to carePlan2)

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan1),
            metadata = metadata
        )

        val request =
            CarePlanPublish.CarePlanPublishCarePlanRequest(
                listOf(event1),
                carePlanService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(1, resourcesByKeys.size)

        val actualCarePlan = resourcesByKeys.entries.first().value.first()
        assertEquals(carePlan2, actualCarePlan)
    }

    @Test
    fun `published care plan with activity but wrong extension doesn't load`() {
        val carePlan1 = carePlan {
            subject of reference("Patient", "5678")
            activity of listOf(
                carePlanActivity {
                    extension of listOf(
                        extension {
                            url of Uri("NotTheExtensionYoureLookingFor")
                            value of DynamicValues.reference(
                                reference("CarePlan", "ChildPlanId")
                            )
                        }
                    )
                }
            )
        }

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan1),
            metadata = metadata
        )

        val request =
            CarePlanPublish.CarePlanPublishCarePlanRequest(
                listOf(event1),
                carePlanService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(0, resourcesByKeys.size)
    }

    @Test
    fun `published care plan with activity but wrong reference type doesn't load`() {
        val carePlan1 = carePlan {
            subject of reference("Patient", "5678")
            activity of listOf(
                carePlanActivity {
                    extension of listOf(
                        extension {
                            url of Uri("http://open.epic.com/FHIR/StructureDefinition/extension/cycle")
                            value of DynamicValues.reference(
                                reference("Patient", "ChildPlanId")
                            )
                        }
                    )
                }
            )
        }

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan1),
            metadata = metadata
        )

        val request =
            CarePlanPublish.CarePlanPublishCarePlanRequest(
                listOf(event1),
                carePlanService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(0, resourcesByKeys.size)
    }

    @Test
    fun `published care plan with activity but null value doesn't load`() {
        val carePlan1 = carePlan {
            subject of reference("Patient", "5678")
            activity of listOf(
                carePlanActivity {
                    extension of listOf(
                        extension {
                            url of Uri("http://open.epic.com/FHIR/StructureDefinition/extension/cycle")
                            value of null
                        }
                    )
                }
            )
        }

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan1),
            metadata = metadata
        )

        val request =
            CarePlanPublish.CarePlanPublishCarePlanRequest(
                listOf(event1),
                carePlanService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(0, resourcesByKeys.size)
    }

    @Test
    fun `published care plan with activity but wrong value type doesn't load`() {
        val carePlan1 = carePlan {
            subject of reference("Patient", "5678")
            activity of listOf(
                carePlanActivity {
                    extension of listOf(
                        extension {
                            url of Uri("http://open.epic.com/FHIR/StructureDefinition/extension/cycle")
                            value of DynamicValues.boolean(false)
                        }
                    )
                }
            )
        }

        val event1 = InteropResourcePublishV1(
            tenantId = tenantId,
            resourceType = ResourceType.CarePlan,
            resourceJson = JacksonManager.objectMapper.writeValueAsString(carePlan1),
            metadata = metadata
        )

        val request =
            CarePlanPublish.CarePlanPublishCarePlanRequest(
                listOf(event1),
                carePlanService,
                tenant
            )
        val resourcesByKeys = request.loadResources(request.requestKeys.toList())
        assertEquals(0, resourcesByKeys.size)
    }
}
