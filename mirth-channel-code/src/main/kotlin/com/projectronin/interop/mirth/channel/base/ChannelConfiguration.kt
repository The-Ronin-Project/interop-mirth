package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.models.Datatype
import com.projectronin.interop.mirth.models.channel.ChannelConfig
import com.projectronin.interop.mirth.models.channel.MirthChannel
import com.projectronin.interop.mirth.models.polling.IntervalPollingConfig
import com.projectronin.interop.mirth.models.polling.PollingConfig
import com.projectronin.interop.mirth.spring.SpringUtil

abstract class ChannelConfiguration<T : MirthChannel> : ChannelConfig<T> {
    override val pollingConfig: PollingConfig = IntervalPollingConfig()
    override val sourceThreads: Int = 1
    override val datatype: Datatype = Datatype.RAW

    override val isStartOnDeploy: Boolean = true
    override val daysUntilPruned: Int = 14
    override val storeAttachments: Boolean = false

    fun create() = SpringUtil.applicationContext.getBean(channelClass.java)
}
