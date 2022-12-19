package com.projectronin.interop.mirth.channel

import com.projectronin.interop.mirth.channel.base.ChannelService
import com.projectronin.interop.mirth.spring.SpringUtil

/**
 * Base factory for create an instance of a [ChannelService]
 */
abstract class ChannelFactory<T : ChannelService> {
    /**
     * Creates an instance of the [ChannelService] supported by this factory.
     */
    @Suppress("UNCHECKED_CAST")
    fun create(): T = SpringUtil.applicationContext.getBean(this.javaClass.declaringClass) as T
}
