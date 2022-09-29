package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.mirth
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Task for running docker compose.
 */
open class DockerComposeTask @Inject constructor(private val execOperations: ExecOperations) : BaseMirthTask() {
    @TaskAction
    fun up() {
        logger.lifecycle("Removing stopped containers")
        execOperations.exec {
            workingDir(project.mirth().dockerDirectory.get())
            isIgnoreExitValue = true
            commandLine("docker compose version".split(" "))
            // commandLine("docker compose rm -f -v mockehrinit".split(" "))
        }
        logger.lifecycle("Running Docker compose")
        execOperations.exec {
            workingDir(project.mirth().dockerDirectory.get())
            commandLine("docker compose up -d --quiet-pull".split(" "))
        }
        logger.lifecycle("Docker compose completed")
    }
}
