package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.ehr.CarePlanService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.datatype.DynamicValueType
import com.projectronin.interop.fhir.r4.datatype.Reference
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninCarePlan
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.PublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishReferenceResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.ResourceRequestKey
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class CarePlanPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninCarePlan,
) : KafkaEventResourcePublisher<CarePlan>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
        profileTransformer,
    ) {
    override val cacheAndCompareResults: Boolean = true

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<CarePlan> {
        return when (val resourceType = events.first().resourceType) {
            ResourceType.Patient ->
                PatientPublishCarePlanRequest(
                    events,
                    vendorFactory.carePlanService,
                    tenant,
                )

            ResourceType.CarePlan ->
                CarePlanPublishCarePlanRequest(
                    events,
                    vendorFactory.carePlanService,
                    tenant,
                )

            else -> throw IllegalStateException("Received resource type ($resourceType) that cannot be used to load care plans")
        }
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<CarePlan> {
        return LoadCarePlanRequest(events, vendorFactory.carePlanService, tenant)
    }

    internal class PatientPublishCarePlanRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: CarePlanService,
        override val tenant: Tenant,
    ) : PublishResourceRequest<CarePlan>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<CarePlan>> {
            return requestFhirIds.associateWith {
                fhirService.findPatientCarePlans(
                    tenant,
                    it,
                    startDate = startDate?.toLocalDate() ?: LocalDate.now().minusMonths(1),
                    endDate = endDate?.toLocalDate() ?: LocalDate.now().plusMonths(1),
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class CarePlanPublishCarePlanRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: CarePlanService,
        override val tenant: Tenant,
    ) : PublishReferenceResourceRequest<CarePlan>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { CarePlanPublishEvent(it, tenant) }

        private class CarePlanPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            PublishResourceEvent<CarePlan>(publishEvent, CarePlan::class) {
            private val epicCycleExtension = "http://open.epic.com/FHIR/StructureDefinition/extension/cycle"
            override val requestKeys: Set<ResourceRequestKey> =
                sourceResource.activity.map { activity ->
                    activity.extension.mapNotNull { extension ->
                        if (extension.url?.value != epicCycleExtension) {
                            null
                        } else {
                            extension.value?.let { extensionValue ->
                                if (extensionValue.type == DynamicValueType.REFERENCE) {
                                    (extensionValue.value as? Reference).let {
                                        if (it?.decomposedType() == "CarePlan") it.decomposedId() else null
                                    }
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }.flatten().map {
                    ResourceRequestKey(
                        metadata.runId,
                        ResourceType.CarePlan,
                        tenant,
                        it,
                    )
                }.toSet()
        }
    }

    internal class LoadCarePlanRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: CarePlanService,
        tenant: Tenant,
    ) : LoadResourceRequest<CarePlan>(loadEvents, tenant)
}
