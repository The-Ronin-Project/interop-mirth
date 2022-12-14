package com.projectronin.interop.mirth.connector

import com.projectronin.interop.aidbox.PatientService
import com.projectronin.interop.aidbox.PractitionerService
import com.projectronin.interop.aidbox.PublishService
import com.projectronin.interop.datalake.DatalakePublishService
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.fhir.ronin.conceptmap.ConceptMapClient
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.db.DBQueueService
import com.projectronin.interop.queue.kafka.KafkaQueueService
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class ServiceFactoryTest {

    lateinit var service: ServiceFactory
    lateinit var springContext: AnnotationConfigApplicationContext
    private val tenantService: TenantService = mockk()
    private val ehrFactory: EHRFactory = mockk()

    @BeforeEach
    fun setupEnvironment() {
        springContext = mockk()
        mockkConstructor(com.projectronin.interop.publishers.PublishService::class)
        every {
            anyConstructed<com.projectronin.interop.publishers.PublishService>().publishFHIRResources(
                any(),
                any()
            )
        } returns true
        every { springContext.getBean(TenantService::class.java) } returns tenantService
        every { springContext.getBean(EHRFactory::class.java) } returns ehrFactory
        every { springContext.getBean(PublishService::class.java) } returns mockk()
        every { springContext.getBean(DatalakePublishService::class.java) } returns mockk()
        every { springContext.getBean(KafkaQueueService::class.java) } returns mockk()
        every { springContext.getBean(DBQueueService::class.java) } returns mockk()
        mockkObject(ServiceFactoryImpl)
        every { ServiceFactoryImpl.applicationContext } returns springContext
    }

    @AfterEach
    fun unmockk() {
        unmockkAll()
    }

    @Test
    fun `factory creates VendorFactory for tenant`() {
        val fake = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("mdaoc") } returns fake

        every { ehrFactory.getVendorFactory(fake) } returns mockk()

        val tenant = ServiceFactoryImpl.getTenant("mdaoc")
        assertNotNull(ServiceFactoryImpl.vendorFactory(tenant))
    }

    @Test
    fun `factory creates VendorFactory for tenant id`() {
        assertNotNull(ServiceFactoryImpl.vendorFactory("mdaoc"))
    }

    @Test
    fun `factory creates PublishService`() {
        assertNotNull(ServiceFactoryImpl.publishService())
    }

    @Test
    fun `factory creates PractitionerService`() {
        every { springContext.getBean(PractitionerService::class.java) } returns mockk()
        assertNotNull(ServiceFactoryImpl.practitionerService())
    }

    @Test
    fun `factory creates PatientService`() {
        every { springContext.getBean(PatientService::class.java) } returns mockk()
        assertNotNull(ServiceFactoryImpl.patientService())
    }

    @Test
    fun `factory creates TenantConfigurationService`() {
        assertNotNull(ServiceFactoryImpl.tenantConfigurationFactory())
    }

    @Test
    fun `dequeue works`() {
        every { springContext.getBean(QueueService::class.java) } returns mockk()
        assertNotNull(ServiceFactoryImpl.queueService())
    }

    @Test
    fun `conceptMap works`() {
        every { springContext.getBean(ConceptMapClient::class.java) } returns mockk()
        assertNotNull(ServiceFactoryImpl.conceptMapClient())
    }

    @Test
    fun `transformManager works`() {
        every { springContext.getBean(TransformManager::class.java) } returns mockk()
        assertNotNull(ServiceFactoryImpl.transformManager())
    }
    @Test
    fun `kafka works`() {
        every { springContext.getBean(KafkaQueueService::class.java) } returns mockk()
        assertNotNull(ServiceFactoryImpl.kafkaQueueService())
    }
}
