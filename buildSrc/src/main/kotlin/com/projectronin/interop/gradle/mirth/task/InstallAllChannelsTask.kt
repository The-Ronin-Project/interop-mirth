package com.projectronin.interop.gradle.mirth.task

import org.gradle.api.tasks.TaskAction

open class InstallAllChannelsTask : BaseInstallChannelTask() {
    @TaskAction
    fun installAllChannels() {
        val channelDirectories = getChannelDirectories()
        val directoryListing = channelDirectories.sortedBy { it.name }.joinToString(", ") { it.name }
        logger.lifecycle("Identified ${channelDirectories.size} channels: $directoryListing")

        channelDirectories.forEach {
            // Blank line before processing for improved readability.
            logger.lifecycle("")
            installChannel(it.name)
        }
    }
}
