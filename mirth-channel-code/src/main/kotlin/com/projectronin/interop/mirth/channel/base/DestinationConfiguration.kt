package com.projectronin.interop.mirth.channel.base

sealed interface DestinationConfiguration {
    val name: String
    val threadCount: Int
    val queueEnabled: Boolean
    val queueBufferSize: Int
}

data class JavaScriptDestinationConfiguration(
    override val name: String,
    override val threadCount: Int = 5,
    override val queueEnabled: Boolean = true,
    override val queueBufferSize: Int = 1000
) : DestinationConfiguration

data class MLLPDestinationConfiguration(
    override val name: String,
    override val threadCount: Int = 5,
    override val queueEnabled: Boolean = true,
    override val queueBufferSize: Int = 1000,
    val remoteAddress: String = "\${ADDRESS}",
    val remotePort: String = "\${PORT}"
) : DestinationConfiguration

data class ChannelDestinationConfiguration(
    override val name: String,
    override val threadCount: Int = 5,
    override val queueEnabled: Boolean = true,
    override val queueBufferSize: Int = 1000,
    val channelId: String,
    val channelTemplate: String,
    val variables: List<String>
) : DestinationConfiguration
