package com.projectronin.interop.mirth.channel

import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.spring.SpringUtil
import com.projectronin.interop.tenant.config.TenantService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class ChannelFactoryTest {
    @BeforeEach
    fun setupEnvironment() {
        mockkObject(SpringUtil)

        // We return an actual application context, but it uses our faked out data.
        every { SpringUtil.applicationContext } returns AnnotationConfigApplicationContext(TestSpringConfig::class.java)
    }

    @AfterEach
    fun unmockk() {
        unmockkAll()
    }

    @Test
    fun `can create class`() {
        val service = TestChannelService.create()
        assertNotNull(service)
        assertInstanceOf(TestChannelService::class.java, service)
    }
}

@Configuration
class TestSpringConfig {
    @Bean
    fun tenantService() = mockk<TenantService>()

    @Bean
    fun transformManager() = mockk<TransformManager>()

    @Bean
    fun testChannelService() = TestChannelService()
}

class TestChannelService :
    ChannelService() {
    companion object : ChannelFactory<TestChannelService>()

    override val rootName: String = "test"
    override val destinations: Map<String, DestinationService> = emptyMap()

    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        TODO("Not yet implemented")
    }
}
