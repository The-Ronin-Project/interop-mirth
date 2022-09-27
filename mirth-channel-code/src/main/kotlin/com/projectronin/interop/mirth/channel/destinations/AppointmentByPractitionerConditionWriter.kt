package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.ehr.inputs.FHIRSearchToken
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.resource.Condition
import com.projectronin.interop.fhir.r4.valueset.ConditionCategoryCodes
import com.projectronin.interop.fhir.r4.valueset.ConditionClinicalStatusCodes
import com.projectronin.interop.fhir.ronin.resource.RoninConditions
import com.projectronin.interop.fhir.ronin.util.unlocalize
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.connector.ServiceFactory

private val clinicalValueSet = CodeSystem.CONDITION_CLINICAL.uri.value
private val categoryValueSet = CodeSystem.CONDITION_CATEGORY.uri.value

class AppointmentByPractitionerConditionWriter(rootName: String, serviceFactory: ServiceFactory) :
    DestinationService(rootName, serviceFactory) {
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

        val tenant = serviceFactory.getTenant(tenantMnemonic)
        val vendorFactory = serviceFactory.vendorFactory(tenant)

        val conditions: List<Condition> =
            vendorFactory.conditionService.findConditionsByCodes(
                tenant,
                patientFHIRId.unlocalize(tenant),
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

        val transformedConditions = transformToList(tenantMnemonic, conditions, RoninConditions)
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
