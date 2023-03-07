package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.fhir.r4.valueset.ConditionClinicalStatusCodes
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import org.springframework.stereotype.Component

private val clinicalValueSet = CodeSystem.CONDITION_CLINICAL.uri.value
private val categoryValueSet = CodeSystem.CONDITION_CATEGORY.uri.value

@Component
class AppointmentByPractitionerConditionWriter(
    tenantService: TenantService,
    transformManager: TransformManager,
    publishService: PublishService,
    private val ehrFactory: EHRFactory,
    private val roninConditions: RoninConditions
) :
    DestinationService(tenantService, transformManager, publishService) {
    /**
     * retrieves all conditions matching clinicalStatus and category codes for a given patient,
     * transforms them and publishes them
     */
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val patientFHIRId = sourceMap[MirthKey.PATIENT_FHIR_ID.code] as String?
            ?: return MirthResponse(MirthResponseStatus.ERROR, message = "No Patient FHIR ID found in channel map")

        val tenant = getTenant(tenantMnemonic)
        val vendorFactory = ehrFactory.getVendorFactory(tenant)

        val conditions: List<Condition> =
            vendorFactory.conditionService.findConditionsByCodes(
                tenant,
                patientFHIRId,
                listOf(
                    FHIRSearchToken(categoryValueSet, ConditionCategoryCodes.PROBLEM_LIST_ITEM.code),
                    FHIRSearchToken(categoryValueSet, ConditionCategoryCodes.ENCOUNTER_DIAGNOSIS.code)
                ),
                listOf(
                    FHIRSearchToken(clinicalValueSet, ConditionClinicalStatusCodes.ACTIVE.code)
                )
            )
        if (conditions.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.SENT,
                message = "No Conditions found for Patient"
            )
        }

        val transformedConditions = transformToList(tenantMnemonic, conditions, roninConditions)
        if (transformedConditions.isEmpty()) {
            return MirthResponse(
                status = MirthResponseStatus.ERROR,
                detailedMessage = "$conditions",
                message = "Failed to transform Conditions for Patient"
            )
        }
        return publishResources(tenantMnemonic, transformedConditions, "Condition")
    }
}
