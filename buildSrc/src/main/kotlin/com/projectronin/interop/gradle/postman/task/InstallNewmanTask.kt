package com.projectronin.interop.gradle.postman.task

import com.projectronin.interop.gradle.mirth.task.BaseMirthTask
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Task for installing the Newman docker container on the host system. This is required by [PostmanTestTask].
 */
open class InstallNewmanTask @Inject constructor(private val execOperations: ExecOperations) : BaseMirthTask() {
    @TaskAction
    fun installNewmanDocker() {
        execOperations.exec {
            commandLine("docker pull postman/newman".split(" "))
        }
    }
}
