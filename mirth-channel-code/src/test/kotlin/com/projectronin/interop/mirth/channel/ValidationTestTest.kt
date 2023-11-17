package com.projectronin.interop.mirth.channel

import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.mirth.channel.destinations.ValidationTestDestination
import com.projectronin.interop.tenant.config.TenantService
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ValidationTestTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var validationTest: ValidationTest

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        (1..500).forEach { _ ->
            // This requires a unique one for each, so just putting in a lot
            mockWebServer.enqueue(
                MockResponse().setHeader("Content-Location", "1234")
            )
        }
        mockWebServer.start()

        val httpClient = HttpSpringConfig().getHttpClient()
        val tenantService = mockk<TenantService> {
            every { getTenantForMnemonic("epicmock") } returns mockk {
                every { vendor.serviceEndpoint } returns mockWebServer.url("/epic").toString()
            }
            every { getTenantForMnemonic("cernmock") } returns mockk {
                every { vendor.serviceEndpoint } returns mockWebServer.url("/cerner/fhir/r4").toString()
            }
            every { getTenantForMnemonic("other") } returns mockk {
                every { vendor.serviceEndpoint } returns mockWebServer.url("/fhir/r4").toString()
            }
        }
        val destination = mockk<ValidationTestDestination>()
        val loadService = mockk<KafkaLoadService> {
            every { pushLoadEvent(any(), DataTrigger.AD_HOC, any(), any(), any()) } returns mockk()
        }

        validationTest = ValidationTest(httpClient, tenantService, destination, loadService)
    }

    @Test
    fun runs() {
        val ignoredTypes =
            "Appointment,CarePlan,MedicationRequest,MedicationStatement,MedicationAdministration,Encounter"

        val serviceMap =
            mapOf(
                "validationTestTenants" to "epicmock,cernmock,other",
                "other-validationIgnoreTypes" to ignoredTypes
            )
        val messages = validationTest.channelSourceReader(serviceMap)
        assertEquals(3, messages.size)
    }
}
