package com.projectronin.interop.mirth.connector

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient
import com.projectronin.interop.mirth.connector.ehr.EpicServiceFactory.epicVendorFactory
import com.projectronin.interop.mirth.connector.util.AidboxUtil.aidBoxPatientService
import com.projectronin.interop.mirth.connector.util.AidboxUtil.aidBoxPractitionerService
import com.projectronin.interop.mirth.connector.util.AidboxUtil.aidboxPublishService
import com.projectronin.interop.mirth.connector.util.OciUtil
import com.projectronin.interop.mirth.connector.util.OciUtil.datalakePublishService
import com.projectronin.interop.mirth.connector.util.QueueUtil.queueService
import com.projectronin.interop.mirth.connector.util.TenantUtil.tenantService
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.model.Tenant

interface ServiceFactory {
    /**
     * Retrieves the [Tenant] for the supplied [tenantId].
     */
    fun getTenant(tenantId: String): Tenant

    /**
     * The [TenantConfigurationFactory].
     */
    fun tenantConfigurationFactory(): TenantConfigurationFactory

    /**
     * The [VendorFactory] for the supplied [tenant]. The VendorFactory can be used to access any EHR-specific implementation needed.
     */
    fun vendorFactory(tenant: Tenant): VendorFactory

    /**
     * The [VendorFactory] for the supplied [tenantId]. The VendorFactory can be used to access any EHR-specific implementation needed.
     */
    fun vendorFactory(tenantId: String): VendorFactory

    /**
     * For writing to the Ronin data store for any tenant.
     */
    fun publishService(): PublishService

    /**
     * For reading Practitioner data from the Ronin data store for any tenant.
     */
    fun practitionerService(): PractitionerService

    /**
     * For reading Patient data from the Ronin data store for any tenant.
     */
    fun patientService(): PatientService

    /**
     * For reading API Messages from the queue.
     */
    fun queueService(): QueueService

    /**
     * For requests to the ConceptMap Registry.
     */
    fun conceptMapClient(): ConceptMapClient
}

/**
 * Provides Mirth with access to Kotlin services.
 */
object ServiceFactoryImpl : ServiceFactory {
    private val ehrFactory = EHRFactory(listOf(epicVendorFactory))

    private val publishService = PublishService(aidboxPublishService, datalakePublishService)

    override fun getTenant(tenantId: String): Tenant =
        tenantService.getTenantForMnemonic(tenantId)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantId")

    override fun tenantConfigurationFactory(): TenantConfigurationFactory = TenantConfigurationFactoryImpl

    override fun vendorFactory(tenant: Tenant): VendorFactory = ehrFactory.getVendorFactory(tenant)

    override fun vendorFactory(tenantId: String): VendorFactory = vendorFactory(getTenant(tenantId))

    override fun publishService(): PublishService = publishService

    override fun practitionerService(): PractitionerService = aidBoxPractitionerService

    override fun patientService(): PatientService = aidBoxPatientService

    override fun queueService(): QueueService = queueService

    override fun conceptMapClient(): ConceptMapClient = OciUtil.conceptMapClient
}
