package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.InteropResourceLoadV1
import com.projectronin.event.interop.internal.v1.InteropResourcePublishV1
import com.projectronin.interop.ehr.PatientService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.ronin.resource.RoninPatient
import com.projectronin.interop.fhir.ronin.transform.TransformManager
import com.projectronin.interop.mirth.channel.base.kafka.KafkaEventResourcePublisher
import com.projectronin.interop.mirth.channel.base.kafka.request.LoadResourceRequest
import com.projectronin.interop.mirth.channel.base.kafka.request.PublishResourceRequest
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import org.springframework.stereotype.Component

@Component
class PatientPublish(
    ehrFactory: EHRFactory,
    publishService: PublishService,
    tenantService: TenantService,
    transformManager: TransformManager,
    profileTransformer: RoninPatient,
) : KafkaEventResourcePublisher<Patient>(
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
    ): PublishResourceRequest<Patient> {
        throw IllegalStateException("Patient does not listen to Publish Events")
    }

    override fun convertLoadEventsToRequest(
        events: List<InteropResourceLoadV1>,
        vendorFactory: VendorFactory,
        tenant: Tenant,
    ): LoadResourceRequest<Patient> {
        return LoadPatientRequest(events, vendorFactory.patientService, tenant)
    }

    internal class LoadPatientRequest(
        loadEvents: List<InteropResourceLoadV1>,
        override val fhirService: PatientService,
        tenant: Tenant,
    ) : LoadResourceRequest<Patient>(loadEvents, tenant)
}
