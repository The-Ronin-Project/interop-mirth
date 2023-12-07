package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.aidbox.AidboxPublishService
import com.projectronin.interop.aidbox.auth.AidboxAuthenticationBroker
import com.projectronin.interop.aidbox.auth.AidboxAuthenticationService
import com.projectronin.interop.aidbox.auth.AidboxCredentials
import com.projectronin.interop.aidbox.client.AidboxClient
import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.r4.resource.Bundle
import com.projectronin.interop.gradle.mirth.mirth
import org.gradle.api.tasks.TaskAction
import java.io.File

open class InstallAidboxResources : BaseMirthTask() {
    @TaskAction
    fun publishResources() {
        val config = project.mirth().channel.aidBoxConfig
        val clientId = System.getenv(config.clientIdKey)
        val clientKey = System.getenv(config.clientSecretKey)
        val restUrl = config.url
        val aidboxCredentials = AidboxCredentials(clientId, clientKey)
        val authService = AidboxAuthenticationService(HttpSpringConfig().getHttpClient(), restUrl, aidboxCredentials)
        val authBroker = AidboxAuthenticationBroker(authService)
        val aidboxClient = AidboxClient(HttpSpringConfig().getHttpClient(), restUrl, authBroker)
        val aidboxPublishService = AidboxPublishService(aidboxClient)
        getAidboxResources().forEach { publishResource(aidboxPublishService, it) }
    }

    private fun getAidboxResources(): List<File> {
        val channelsDirectory = project.mirth().channel.baseDirectory.get()
        logger.lifecycle("Looking at $channelsDirectory")

        val aidboxDirectories =
            channelsDirectory.asFile.listFiles()?.mapNotNull {
                if (it.isDirectory) {
                    it.listFiles()?.find { c -> c.isDirectory && c.name == "aidbox" }
                } else {
                    null
                }
            } ?: emptyList()

        return aidboxDirectories.flatMap { it.listFiles().toList() }.filter { it.extension == "json" }
    }

    private fun publishResource(
        aidboxPublishService: AidboxPublishService,
        file: File,
    ) {
        val rawBundle = file.readLines().joinToString("\n")
        val bundle = JacksonManager.objectMapper.readValue(rawBundle, Bundle::class.java)
        val resources = bundle.entry.map { it.resource!! }
        aidboxPublishService.publish(resources)
    }
}
