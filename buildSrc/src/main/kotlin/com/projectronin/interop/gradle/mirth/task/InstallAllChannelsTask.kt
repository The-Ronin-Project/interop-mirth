package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.mirth
import org.gradle.api.tasks.TaskAction

open class InstallAllChannelsTask : BaseInstallChannelTask() {
    @TaskAction
    fun installAllChannels() {
        val channels =
            project.mirth().channel.generatedChannelDirectory.get().asFile.listFiles()?.toList() ?: emptyList()
        logger.lifecycle(
            "Identified ${channels.size} channels: ${
            channels.map { it.name }.sorted().joinToString(", ")
            }"
        )

        channels.forEach {
            // Blank line before processing for improved readability.
            logger.lifecycle("")
            installChannel(it.nameWithoutExtension)
        }
    }
}
