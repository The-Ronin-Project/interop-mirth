package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.mirth
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Task for running docker compose.
 */
open class DockerComposeTask @Inject constructor(private val execOperations: ExecOperations) : BaseMirthTask() {
    @TaskAction
    fun up() {
        val os = ByteArrayOutputStream()
        val alreadyRunningCommand = "docker compose ps -q mc --status running"
        execOperations.exec {
            workingDir(project.mirth().dockerDirectory.get())
            standardOutput = os
            isIgnoreExitValue = true
            commandLine(alreadyRunningCommand.split(" "))
        }
        val result = os.toString()
        val notRunning = (result.isNullOrBlank() == true)
        logger.lifecycle("$alreadyRunningCommand == $result")
        logger.lifecycle("notRunning == $notRunning")

        if (notRunning) {
            logger.lifecycle("Running Docker compose")
            execOperations.exec {
                workingDir(project.mirth().dockerDirectory.get())
                commandLine("docker compose up -d --quiet-pull".split(" "))
            }
        }
        logger.lifecycle("Docker compose completed")
    }
}
