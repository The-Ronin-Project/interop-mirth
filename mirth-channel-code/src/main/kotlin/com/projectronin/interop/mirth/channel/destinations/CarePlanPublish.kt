package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.aidbox.utils.findFhirID
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.ehr.CarePlanService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.CarePlan
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.resource.RoninCarePlan
import com.projectronin.interop.mirth.channel.base.KafkaEventResourcePublisher
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

    // turn a kafka event into an abstract class we can deal with
    override fun convertEventToRequest(
        serializedEvent: String,
        eventClassName: String,
        vendorFactory: VendorFactory,
        tenant: Tenant
    ): ResourceLoadRequest<CarePlan> {
        return when (eventClassName) {
            InteropResourcePublishV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourcePublishV1::class)
                PatientSourceCarePlanLoadRequest(event, vendorFactory.carePlanService, tenant)
            }

            InteropResourceLoadV1::class.simpleName!! -> {
                val event = JacksonUtil.readJsonObject(serializedEvent, InteropResourceLoadV1::class)
                CarePlanLoadRequest(event, vendorFactory.carePlanService, tenant)
            }

            else -> throw IllegalStateException("Received a string which cannot deserialize to a known event")
        }
    }

    private class PatientSourceCarePlanLoadRequest(
        sourceEvent: InteropResourcePublishV1,
        override val fhirService: CarePlanService,
        tenant: Tenant
    ) :
        IdBasedPublishEventResourceLoadRequest<CarePlan, Patient>(sourceEvent, tenant) {
        override val sourceResource: Patient = JacksonUtil.readJsonObject(sourceEvent.resourceJson, Patient::class)

        override fun loadResources(): List<CarePlan> {
            val patientFhirId = sourceResource.identifier.findFhirID()
            return fhirService.findPatientCarePlans(
                tenant = tenant,
                patientFhirId = patientFhirId,
                startDate = LocalDate.now().minusYears(1),
                endDate = LocalDate.now().plusYears(1)
            )
        }
    }

    private class CarePlanLoadRequest(
        sourceEvent: InteropResourceLoadV1,
        override val fhirService: CarePlanService,
        tenant: Tenant
    ) : LoadEventResourceLoadRequest<CarePlan>(sourceEvent, tenant)
}
