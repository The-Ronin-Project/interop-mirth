package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.rest.MirthRestClient.Companion.client
import com.projectronin.interop.gradle.mirth.rest.model.DirectoryResource
import com.projectronin.interop.gradle.mirth.rest.model.DirectoryResourceList
import com.projectronin.interop.gradle.mirth.rest.model.INTEROP_DIRECTORY
import com.projectronin.interop.gradle.mirth.rest.model.INTEROP_RESOURCE_ID
import com.projectronin.interop.gradle.mirth.rest.model.MirthList
import io.ktor.http.isSuccess
import org.gradle.api.tasks.TaskAction

/**
 * Task for adding the necessary Interop DirectoryResource to Mirth.
 */
open class AddInteropDirectoryResourceTask : BaseMirthTask() {
    private val defaultResource = DirectoryResource(
        id = "Default Resource",
        name = "[Default Resource]",
        description = "Loads libraries from the custom-lib folder in the Mirth Connect home directory.",
        includeWithGlobalScripts = true,
        directory = "custom-lib",
        directoryRecursion = true
    )
    private val interopResource = DirectoryResource(
        id = INTEROP_RESOURCE_ID,
        name = "Interop",
        description = "Interop resources",
        includeWithGlobalScripts = false,
        directory = INTEROP_DIRECTORY,
        directoryRecursion = true
    )

    @TaskAction
    fun addInteropResources() {
        logger.lifecycle("Adding Interop directory resource")
        val status = client.putResources(MirthList(DirectoryResourceList(listOf(defaultResource, interopResource))))
        if (status.isSuccess()) {
            logger.lifecycle("Interop directory resource successfully added")
        } else {
            throw RuntimeException("Received status code $status while attempting to add Interop directory resource")
        }
    }
}
