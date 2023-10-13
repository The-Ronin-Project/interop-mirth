package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.fhir.ronin.transform.TransformResponse
import com.projectronin.interop.mirth.channel.base.kafka.KafkaQueue
import com.projectronin.interop.mirth.channel.destinations.queue.PatientTenantlessQueueWriter
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.ResourcesNotTransformedException
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

/**
 * This channel reads off the Kafka Queue for Patient messages, transforms them into RoninPatient and publishes them
 */
@Component
class KafkaPatientQueue(
    tenantService: TenantService,
    queueService: KafkaQueueService,
    patientQueueWriter: PatientTenantlessQueueWriter,
    private val transformManager: TransformManager,
    private val roninPatient: RoninPatient
) :
    KafkaQueue<Patient>(tenantService, queueService, patientQueueWriter) {
    companion object {
        fun create() = SpringUtil.applicationContext.getBean(KafkaPatientQueue::class.java)
    }

    override val limit = 3 // this is used as a hack to give the channel a unique group ID
    override val rootName = "KafkaPatientQueue"
    override val resourceType = ResourceType.PATIENT

    override fun deserializeAndTransform(string: String, tenant: Tenant): TransformResponse<Patient> {
        val patient = JacksonUtil.readJsonObject(string, Patient::class)
        return transformManager.transformResource(patient, roninPatient, tenant)
            ?: throw ResourcesNotTransformedException("Failed to transform Patient for tenant ${tenant.mnemonic}")
    }
}
