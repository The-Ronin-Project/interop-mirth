package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.spring.SpringUtil
import kotlin.reflect.KClass

abstract class ChannelConfiguration<T : MirthSource> {
    abstract val channelClass: KClass<T>
    abstract val id: String
    abstract val description: String
    abstract val metadataColumns: Map<String, String>

    open val pollingConfig: PollingConfig = IntervalPollingConfig()
    open val sourceThreads: Int = 1
    open val datatype: Datatype = Datatype.RAW

    open val isStartOnDeploy: Boolean = true
    open val daysUntilPruned: Int = 14
    open val storeAttachments: Boolean = false

    fun create() = SpringUtil.applicationContext.getBean(channelClass.java)

    enum class Datatype {
        RAW,
        HL7V2
    }
}

sealed interface PollingConfig {
    val pollOnStart: Boolean
}

class IntervalPollingConfig(
    override val pollOnStart: Boolean = true,
    val pollingFrequency: Int = 5000
) : PollingConfig

class TimedPollingConfig(
    override val pollOnStart: Boolean = true,
    val hour: Int,
    val minute: Int
) : PollingConfig
