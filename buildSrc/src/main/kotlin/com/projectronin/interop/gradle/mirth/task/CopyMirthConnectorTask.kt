package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.mirth
import com.projectronin.interop.gradle.mirth.rest.model.INTEROP_DIRECTORY
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.dependencies
import java.util.UUID

/**
 * Task for copying the Mirth Connector to the Interop resource directory.
 */
open class CopyMirthConnectorTask : BaseMirthTask() {
    private val downloadConfiguration = project.configurations.create("download")

    init {
        val dependencyAttribute = Attribute.of("org.gradle.dependency.bundling", String::class.java)

        downloadConfiguration.attributes.attribute(dependencyAttribute, "shadowed")

        project.dependencies {
            downloadConfiguration(project.mirth().mirthConnectorLibrary)
        }
    }

    @TaskAction
    fun copy() {
        val resourceDirectory = project.mirth().dockerDirectory.get().dir(INTEROP_DIRECTORY)

        logger.lifecycle("Removing any existing Mirth Connector from the interop resource folder")
        val filesToRemove =
            resourceDirectory.asFileTree.files.filter {
                it.name.startsWith("interop-mirth-connector")
            }
        filesToRemove.forEach { it.delete() }
        logger.lifecycle("Removed ${filesToRemove.size} old files")

        logger.lifecycle("Copying Mirth Connector to interop resource folder")
        project.copy {
            fileMode = 0b110110110 // Results are wonky with a regular base 10 integer
            from(downloadConfiguration.singleFile)
            into(resourceDirectory)
            rename {
                val uuid = UUID.randomUUID()
                it.replace(".jar", "-$uuid.jar")
            }
        }
    }
}
