package com.projectronin.interop.mirth.channel

import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.connector.ServiceFactory
import com.projectronin.interop.mirth.connector.ServiceFactoryImpl

/**
 * Base factory for create an instance of a [ChannelService]
 */
abstract class ChannelFactory<T : ChannelService> {
    /**
     * Creates an instance of the [ChannelService] supported by this factory.
     */
    @Suppress("UNCHECKED_CAST")
    fun create(): T =
        this.javaClass.declaringClass.getConstructor(ServiceFactory::class.java).newInstance(ServiceFactoryImpl) as T
}
