package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.rest.MirthRestClient.Companion.client
import com.projectronin.interop.gradle.mirth.rest.model.Preference
import io.ktor.http.isSuccess
import org.gradle.api.tasks.TaskAction

/**
 * Task for setting up the user in Mirth.
 */
open class SetupUserTask : BaseMirthTask() {
    private val preferences = listOf(
        Preference("firstlogin", "false"),
        Preference("checkForNotifications", "false"),
        Preference("showNotificationPopoup", "false")
    )

    @TaskAction
    fun setupUser() {
        logger.lifecycle("Setting up the admin user in Mirth")

        val user = client.getUser("admin")
        user?.let {
            logger.lifecycle("Found user: $user")

            val currentPreferences = client.getUserPreferences(user.id)
            val newPreferences = (currentPreferences + preferences).associateBy { it.name }.values.toList()

            val status = client.putUserPreferences(user.id, newPreferences)
            if (status.isSuccess()) {
                logger.lifecycle("User preferences updated")
            } else {
                throw IllegalStateException("Error encountered while setting admin password: $status")
            }
        } ?: throw IllegalStateException("No admin user found in Mirth")
    }
}
