package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.rest.MirthRestClient.Companion.client
import com.projectronin.interop.gradle.mirth.rest.model.INTEROP_RESOURCE_ID
import org.gradle.api.tasks.TaskAction

/**
 * Task for reloading the Mirth Interop directory resource.
 */
open class ReloadMirthResourceTask : BaseMirthTask() {
    @TaskAction
    fun reloadResources() {
        logger.lifecycle("Reloading Mirth Interop resource")
        client.reloadResource(INTEROP_RESOURCE_ID)
    }
}
