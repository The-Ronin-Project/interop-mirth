package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.mirth.channel.base.BaseQueue
import com.projectronin.interop.mirth.channel.destinations.queue.PatientQueueWriter
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

/**
 * This channel reads off the API Queue for Patient messages, transforms them into RoninPatient and publishes them
 */
@Component
class PatientQueue(
    tenantService: TenantService,
    transformManager: TransformManager,
    patientQueueWriter: PatientQueueWriter,
    queueService: QueueService,
    private val roninPatient: RoninPatient
) :
    BaseQueue<Patient>(tenantService, transformManager, patientQueueWriter, queueService) {
    companion object : ChannelFactory<PatientQueue>()

    override val rootName = "PatientQueue"
    override val resourceType = ResourceType.PATIENT

    override fun deserializeAndTransform(string: String, tenant: Tenant): Patient {
        val patient = JacksonUtil.readJsonObject(string, Patient::class)
        return transformManager.transformResource(patient, roninPatient, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Patient for tenant ${tenant.mnemonic}")
    }
}
