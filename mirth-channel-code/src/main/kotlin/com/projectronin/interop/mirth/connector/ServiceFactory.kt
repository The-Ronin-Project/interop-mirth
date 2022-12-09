package com.projectronin.interop.mirth.connector

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.datalake.DatalakePublishService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.factory.VendorFactory
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient
import com.projectronin.interop.mirth.connector.util.SpringUtil
import com.projectronin.interop.publishers.PublishService
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import com.projectronin.interop.aidbox.PublishService as AidboxPublishService

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

    /**
     * For transforming to Ronin profiles.
     */
    fun transformManager(): TransformManager
}

/**
 * Provides Mirth with access to Kotlin services.
 */
object ServiceFactoryImpl : ServiceFactory {
    val applicationContext by lazy { SpringUtil.applicationContext }
    private val ehrFactory by lazy { applicationContext.getBean(EHRFactory::class.java) }
    private val publishService by lazy {
        PublishService(
            applicationContext.getBean(AidboxPublishService::class.java),
            applicationContext.getBean(DatalakePublishService::class.java)
        )
    }
    private val tenantService by lazy { applicationContext.getBean(TenantService::class.java) }

    override fun getTenant(tenantId: String): Tenant =
        tenantService.getTenantForMnemonic(tenantId)
            ?: throw IllegalArgumentException("Unknown tenant: $tenantId")

    override fun tenantConfigurationFactory(): TenantConfigurationFactory = TenantConfigurationFactoryImpl

    override fun vendorFactory(tenant: Tenant): VendorFactory = ehrFactory.getVendorFactory(tenant)

    override fun vendorFactory(tenantId: String): VendorFactory = vendorFactory(getTenant(tenantId))

    override fun publishService(): PublishService = publishService

    override fun practitionerService() = applicationContext.getBean(PractitionerService::class.java)

    override fun patientService() = applicationContext.getBean(PatientService::class.java)

    override fun queueService() = applicationContext.getBean(QueueService::class.java)

    override fun conceptMapClient() = applicationContext.getBean(ConceptMapClient::class.java)

    override fun transformManager() = applicationContext.getBean(TransformManager::class.java)
}
