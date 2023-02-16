package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.ConditionService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.fhir.r4.valueset.ConditionClinicalStatusCodes
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class ConditionPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninConditions,
) : KafkaEventResourcePublisher<Condition>(
    tenantService, ehrFactory, transformManager, publishService, profileTransformer
) {

    // turn a kafka event into an abstract class we can deal with
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<Condition> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                PatientSourceConditionLoadRequest(event, vendorFactory.conditionService, tenant)
            }
            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                ConditionLoadRequest(event, vendorFactory.conditionService, tenant)
            }
            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientSourceConditionLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: ConditionService,
        override val tenant: Tenant
    ) : PublishEventResourceLoadRequest<Condition>(sourceEvent) {

        private val clinicalValueSet = CodeSystem.CONDITION_CLINICAL.uri.value
        private val categoryValueSet = CodeSystem.CONDITION_CATEGORY.uri.value

        override fun loadResources(): List<Condition> {
            val patientFhirId = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class).id?.value
            return fhirService.findConditionsByCodes(
                tenant,
                patientFhirId!!.unlocalize(tenant),
                listOf(
                    FHIRSearchToken(categoryValueSet, ConditionCategoryCodes.PROBLEM_LIST_ITEM.code),
                    FHIRSearchToken(categoryValueSet, ConditionCategoryCodes.ENCOUNTER_DIAGNOSIS.code)
                ),
                listOf(
                    FHIRSearchToken(clinicalValueSet, ConditionClinicalStatusCodes.ACTIVE.code)
                )
            )
        }
    }

    private class ConditionLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: ConditionService,
        override val tenant: Tenant
    ) : LoadEventResourceLoadRequest<Condition>(sourceEvent)
}
