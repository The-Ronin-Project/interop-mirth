package com.projectronin.interop.mirth.channel

import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.channel.base.DestinationService
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.connector.ServiceFactory
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChannelFactoryTest {
    @Test
    fun `can create for service with only one, valid constructor`() {
        val service = ValidConstructorChannelService.create()

        assertNotNull(service)
        assertInstanceOf(ValidConstructorChannelService::class.java, service)
    }

    @Test
    fun `can create for service with multiple constructors, including one valid one`() {
        val service = MultipleConstructorChannelService.create()

        assertNotNull(service)
        assertInstanceOf(MultipleConstructorChannelService::class.java, service)
    }

    @Test
    fun `fails for service with empty constructor`() {
        val exception = assertThrows<NoSuchMethodException> {
            NoConstructorChannelService.create()
        }
        assertEquals(
            "com.projectronin.interop.mirth.channel.NoConstructorChannelService.<init>(com.projectronin.interop.mirth.connector.ServiceFactory)",
            exception.message
        )
    }

    @Test
    fun `fails for service with different constructor param`() {
        val exception = assertThrows<NoSuchMethodException> {
            DifferentConstructorParamChannelService.create()
        }
        assertEquals(
            "com.projectronin.interop.mirth.channel.DifferentConstructorParamChannelService.<init>(com.projectronin.interop.mirth.connector.ServiceFactory)",
            exception.message
        )
    }

    @Test
    fun `fails for service with too many constructor params`() {
        val exception = assertThrows<NoSuchMethodException> {
            TooManyConstructorParamsChannelService.create()
        }
        assertEquals(
            "com.projectronin.interop.mirth.channel.TooManyConstructorParamsChannelService.<init>(com.projectronin.interop.mirth.connector.ServiceFactory)",
            exception.message
        )
    }
}

internal class ValidConstructorChannelService(serviceFactory: ServiceFactory) : TestChannelService(serviceFactory) {
    companion object : ChannelFactory<ValidConstructorChannelService>()
}

internal class MultipleConstructorChannelService(serviceFactory: ServiceFactory) : TestChannelService(serviceFactory) {
    companion object : ChannelFactory<MultipleConstructorChannelService>()

    constructor(name: String, serviceFactory: ServiceFactory) : this(serviceFactory)
}

internal class NoConstructorChannelService() : TestChannelService(mockk()) {
    companion object : ChannelFactory<NoConstructorChannelService>()
}

internal class DifferentConstructorParamChannelService(name: String) : TestChannelService(mockk()) {
    companion object : ChannelFactory<DifferentConstructorParamChannelService>()
}

internal class TooManyConstructorParamsChannelService(serviceFactory: ServiceFactory, name: String) :
    TestChannelService(serviceFactory) {
    companion object : ChannelFactory<TooManyConstructorParamsChannelService>()
}

abstract class TestChannelService(serviceFactory: ServiceFactory) : ChannelService(serviceFactory) {
    override val destinations: Map<String, DestinationService>
        get() = TODO("Not yet implemented")

    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        TODO("Not yet implemented")
    }

    override val rootName: String
        get() = TODO("Not yet implemented")
}
