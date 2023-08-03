package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.CarePlanService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninCarePlan
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class CarePlanPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninCarePlan
) : KafkaEventResourcePublisher<CarePlan>(
    tenantService,
    ehrFactory,
    transformManager,
    publishService,
    profileTransformer
) {
    override val cacheAndCompareResults: Boolean = true

    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): PublishResourceRequest<CarePlan> {
        return PatientPublishCarePlanRequest(events, vendorFactory.carePlanService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): LoadResourceRequest<CarePlan> {
        return LoadCarePlanRequest(events, vendorFactory.carePlanService, tenant)
    }

    internal class PatientPublishCarePlanRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: CarePlanService,
        override val tenant: Tenant
    ) : PublishResourceRequest<CarePlan>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        override fun loadResourcesForIds(requestFhirIds: List<String>): Map<String, List<CarePlan>> {
            return requestFhirIds.associateWith {
                fhirService.findPatientCarePlans(
                    tenant,
                    it,
                    startDate = LocalDate.now().minusMonths(1),
                    endDate = LocalDate.now().plusMonths(1)
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class LoadCarePlanRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: CarePlanService,
        tenant: Tenant
    ) : LoadResourceRequest<CarePlan>(loadEvents, tenant)
}
