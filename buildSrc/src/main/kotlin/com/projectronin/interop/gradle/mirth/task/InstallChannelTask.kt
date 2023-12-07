package com.projectronin.interop.gradle.mirth.task

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

open class InstallChannelTask : BaseInstallChannelTask() {
    @Input
    @set:Option(option = "channel", description = "The Channel to install")
    var channel: String = ""

    @TaskAction
    fun installChannel() {
        if (channel.isBlank()) {
            throw IllegalArgumentException("No channel was provided. Please use \"--channel [CHANNEL]\" to define the channel to install.")
        }

        // Let's validate that the provided channel, is a channel
        val channelDirectories = getChannelDirectories()
        val requestedChannel =
            channelDirectories.find { it.name == channel }
                ?: throw IllegalArgumentException("Provided channel is not a valid channel name in the current project")

        installChannel(requestedChannel.name)
    }
}
