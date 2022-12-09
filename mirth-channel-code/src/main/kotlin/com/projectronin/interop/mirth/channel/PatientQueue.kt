package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant

/**
 * This channel reads off the API Queue for Patient messages, transforms them into RoninPatient and publishes them
 */
class PatientQueue(serviceFactory: ServiceFactory) :
    BaseQueue<Patient>(serviceFactory, Patient::class) {
    companion object : ChannelFactory<PatientQueue>()

    override val rootName = "PatientQueue"
    override val resourceType = ResourceType.PATIENT

    override fun deserializeAndTransform(string: String, tenant: Tenant): Patient {
        val patient = JacksonUtil.readJsonObject(string, Patient::class)
        val roninPatient = RoninPatient.create(
            serviceFactory.vendorFactory(tenant).identifierService,
            serviceFactory.conceptMapClient()
        )
        return serviceFactory.transformManager().transformResource(patient, roninPatient, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Patient for tenant ${tenant.mnemonic}")
    }
}
