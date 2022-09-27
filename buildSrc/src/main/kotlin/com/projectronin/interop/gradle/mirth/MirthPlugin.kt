package com.projectronin.interop.gradle.mirth

import com.projectronin.interop.gradle.mirth.task.AddCodeTemplatesTask
import com.projectronin.interop.gradle.mirth.task.AddInteropDirectoryResourceTask
import com.projectronin.interop.gradle.mirth.task.CopyMirthConnectorTask
import com.projectronin.interop.gradle.mirth.task.DockerComposeTask
import com.projectronin.interop.gradle.mirth.task.InstallAidboxResources
import com.projectronin.interop.gradle.mirth.task.InstallAllChannelsTask
import com.projectronin.interop.gradle.mirth.task.InstallChannelTask
import com.projectronin.interop.gradle.mirth.task.ReloadMirthResourceTask
import com.projectronin.interop.gradle.mirth.task.SetupUserTask
import com.projectronin.interop.gradle.mirth.task.UpdateTenantConfigTask
import com.projectronin.interop.gradle.mirth.task.UpdateTenantServerTask
import com.projectronin.interop.gradle.postman.task.InstallNewmanTask
import com.projectronin.interop.gradle.postman.task.PostmanTestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

private const val EXTENSION_NAME = "mirth"

/**
 * Plugin defining a set of tasks for working with Mirth.
 */
class MirthPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create<com.projectronin.interop.gradle.mirth.MirthExtension>(EXTENSION_NAME)

        setupMirthTasks(project)
        setupPostmanTasks(project)
    }

    private fun setupMirthTasks(project: Project) {
        val dockerComposeTask = project.tasks.register<DockerComposeTask>("dockerUp")

        val setupUserTask = project.tasks.register<SetupUserTask>("setupUser") {
            dependsOn(dockerComposeTask)
        }

        val addInteropDirectoryResourceTask =
            project.tasks.register<AddInteropDirectoryResourceTask>("addInteropDirectoryResource") {
                dependsOn(dockerComposeTask)
            }

        val reloadMirthResourceTask = project.tasks.register<ReloadMirthResourceTask>("reloadMirthResource") {
            dependsOn(addInteropDirectoryResourceTask)
        }

        val copyMirthConnectorTask = project.tasks.register<CopyMirthConnectorTask>("copyMirthConnector") {
            dependsOn(":mirth-channel-code:build")
            finalizedBy(reloadMirthResourceTask)
        }

        val addCodeTemplatesTask = project.tasks.register<AddCodeTemplatesTask>("addCodeTemplates") {
            dependsOn(dockerComposeTask)
        }

        val installAllChannelsTask = project.tasks.register<InstallAllChannelsTask>("installAllChannels") {
            dependsOn(addCodeTemplatesTask)
        }

        project.tasks.register<InstallChannelTask>("installChannel") {
            dependsOn(addCodeTemplatesTask)
        }

        val updateTenantConfig = project.tasks.register<UpdateTenantConfigTask>("updateTenantConfig") {
            dependsOn(dockerComposeTask)
        }

        val updateTenantServer = project.tasks.register<UpdateTenantServerTask>("updateTenantServer") {
            dependsOn(dockerComposeTask)
        }

        val installAidboxResources = project.tasks.register<InstallAidboxResources>("installAidboxResources") {
            dependsOn(dockerComposeTask)
        }

        project.tasks.register("mirth") {
            dependsOn(dockerComposeTask)
            dependsOn(setupUserTask)
            dependsOn(addInteropDirectoryResourceTask)
            dependsOn(copyMirthConnectorTask)
            dependsOn(reloadMirthResourceTask)
            dependsOn(addCodeTemplatesTask)
            dependsOn(installAllChannelsTask)
            dependsOn(updateTenantConfig)
            dependsOn(updateTenantServer)
            dependsOn(installAidboxResources)
        }
    }

    private fun setupPostmanTasks(project: Project) {
        val installNewman = project.tasks.register<InstallNewmanTask>("installNewman")
        val postmanTests = project.tasks.register<PostmanTestTask>("postmanTests") {
            dependsOn(installNewman)
            dependsOn(project.tasks.named("mirth"))
        }

        project.tasks.getByName("test") {
            dependsOn(postmanTests)
        }
    }
}

/**
 * Helper method for accessing the [MirthExtension] throughout this project.
 */
internal fun Project.mirth(): com.projectronin.interop.gradle.mirth.MirthExtension = extensions.getByName(EXTENSION_NAME) as com.projectronin.interop.gradle.mirth.MirthExtension
