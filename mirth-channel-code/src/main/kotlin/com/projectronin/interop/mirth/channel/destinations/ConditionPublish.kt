package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.resource.load.v1.InteropResourceLoadV1
import com.projectronin.event.interop.resource.publish.v1.InteropResourcePublishV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.ConditionService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.fhir.r4.valueset.ConditionClinicalStatusCodes
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class ConditionPublish(
    private val tenantService: TenantService,
    private val ehrFactory: EHRFactory,
    private val transformManager: TransformManager,
    private val roninConditions: RoninConditions,
    private val publishService: PublishService,
) : TenantlessDestinationService() {

    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val tenant = tenantService.getTenantForMnemonic(tenantMnemonic)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantMnemonic")
        val vendorFactory = ehrFactory.getVendorFactory(tenant)
        val eventClassName = sourceMap[MirthKey.KAFKA_EVENT.code] ?: throw MapVariableMissing("Missing Event Name")
        val conditionLoadRequest = convertEventToRequest(msg, eventClassName as String)
        val resources = runCatching {
            conditionLoadRequest.loadConditions(tenant, vendorFactory.conditionService)
        }.fold(
            onSuccess = {
                it
            },
            onFailure = {
                logger.error(it) { "Failed to retrieve conditions from EHR" }
                return MirthResponse(
                    status = MirthResponseStatus.ERROR,
                    detailedMessage = it.message,
                    message = "Failed EHR Call"
                )
            }
        )

        val transformedResources = resources.mapNotNull {
            transformManager.transformResource(it, roninConditions, tenant)
        }
        if (transformedResources.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = resources.truncateList(),
                message = "Failed to transform ${resources.size} condition(s)"
            )
        }

        if (!publishService.publishFHIRResources(
                tenantMnemonic,
                transformedResources,
                conditionLoadRequest.dataTrigger
            )
        ) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = transformedResources.truncateList(),
                message = "Failed to publish ${transformedResources.size} condition(s)"
            )
        }

        return MirthResponse(
            status = MirthResponseStatus.SENT,
            detailedMessage = transformedResources.truncateList(),
            message = "Published ${transformedResources.size} conditions(s)",
        )
    }

    fun List<Condition>.truncateList(): String {
        val list = when {
            this.size > 5 -> this.map { it.id?.value }
            else -> this
        }
        return JacksonUtil.writeJsonValue(list)
    }

    // turn a kafka event into an abstract class we can deal with
    private fun convertEventToRequest(msg: String, eventClassName: String): ConditionLoadRequest {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(msg, InteropResourcePublishV1::class)
                PatientSourceConditionLoad(event)
            }
            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(msg, InteropResourceLoadV1::class)
                AdHocSourceConditionLoad(event)
            }
            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    abstract class ConditionLoadRequest() {
        abstract val sourceEvent: Any
        abstract val dataTrigger: DataTrigger
        abstract fun loadConditions(tenant: Tenant, conditionService: ConditionService): List<Condition>
    }

    private class PatientSourceConditionLoad(override val sourceEvent: InteropResourcePublishV1) : ConditionLoadRequest() {
        private val clinicalValueSet = CodeSystem.CONDITION_CLINICAL.uri.value
        private val categoryValueSet = CodeSystem.CONDITION_CATEGORY.uri.value

        override val dataTrigger: DataTrigger = when (sourceEvent.dataTrigger) {
            InteropResourcePublishV1.DataTrigger.adhoc -> DataTrigger.AD_HOC
            InteropResourcePublishV1.DataTrigger.nightly -> DataTrigger.NIGHTLY
            else -> {
                // backfill
                throw IllegalStateException("Received a data trigger which cannot be transformed to a known value")
            }
        }

        override fun loadConditions(tenant: Tenant, conditionService: ConditionService): List<Condition> {
            val patientFhirId = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class).id?.value
            return conditionService.findConditionsByCodes(
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

    private class AdHocSourceConditionLoad(override val sourceEvent: InteropResourceLoadV1) : ConditionLoadRequest() {
        override val dataTrigger: DataTrigger = DataTrigger.AD_HOC
        override fun loadConditions(tenant: Tenant, conditionService: ConditionService): List<Condition> {
            return listOf(conditionService.getByID(tenant, sourceEvent.resourceFHIRId))
        }
    }
}
