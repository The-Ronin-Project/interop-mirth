package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.ConditionService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.event.IdBasedPublishResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.event.ResourceEvent
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class ConditionPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninConditions,
) : KafkaEventResourcePublisher<Condition>(
        tenantService,
        ehrFactory,
        transformManager,
        publishService,
        profileTransformer,
    ) {
    override fun convertPublishEventsToRequest(
        events: List<InteropResourcePublishV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): PublishResourceRequest<Condition> {
        return PatientPublishConditionRequest(events, vendorFactory.conditionService, tenant)
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<Condition> {
        return LoadConditionRequest(events, vendorFactory.conditionService, tenant)
    }

    internal class PatientPublishConditionRequest(
        publishEvents: List<InteropResourcePublishV1>,
        override val fhirService: ConditionService,
        override val tenant: Tenant,
    ) : PublishResourceRequest<Condition>() {
        override val sourceEvents: List<ResourceEvent<InteropResourcePublishV1>> =
            publishEvents.map { PatientPublishEvent(it, tenant) }

        private val categorySystem = CodeSystem.CONDITION_CATEGORY.uri.value
        private val categoryHealthConcernSystem = CodeSystem.CONDITION_CATEGORY_HEALTH_CONCERN.uri.value

        override fun loadResourcesForIds(
            requestFhirIds: List<String>,
            startDate: OffsetDateTime?,
            endDate: OffsetDateTime?,
        ): Map<String, List<Condition>> {
            return requestFhirIds.associateWith {
                fhirService.findConditionsByCodes(
                    tenant,
                    it,
                    listOf(
                        FHIRSearchToken(categorySystem, ConditionCategoryCodes.PROBLEM_LIST_ITEM.code),
                        FHIRSearchToken(categoryHealthConcernSystem, ConditionCategoryCodes.HEALTH_CONCERN.code),
                        FHIRSearchToken(categorySystem, ConditionCategoryCodes.ENCOUNTER_DIAGNOSIS.code),
                    ),
                )
            }
        }

        private class PatientPublishEvent(publishEvent: InteropResourcePublishV1, tenant: Tenant) :
            IdBasedPublishResourceEvent<Patient>(publishEvent, tenant, Patient::class)
    }

    internal class LoadConditionRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: ConditionService,
        tenant: Tenant,
    ) : LoadResourceRequest<Condition>(loadEvents, tenant)
}
