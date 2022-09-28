package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.mirth
import com.projectronin.interop.gradle.tenant.rest.TenantRestClient
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.util.Properties

abstract class BaseTenantServerTask : BaseMirthTask() {
    @get:Input
    abstract val subfolder: String

    @TaskAction
    fun updateTenantConfig() {
        logger.lifecycle("Updating tenant server information")

        val allTenantConfigs = loadTenantConfigs()
        logger.lifecycle("tenant server 1")
        val tenantConfig = project.mirth().channel.tenantConfig
        val defaultMnemonic = tenantConfig.defaultMnemonic
        logger.lifecycle("tenant server 2")
        logger.lifecycle(tenantConfig.toString())
        logger.lifecycle(tenantConfig.defaultMnemonic)
        val client = TenantRestClient.createClient(tenantConfig.auth)
        logger.lifecycle("tenant server 3")

        for ((tenantName, config) in allTenantConfigs) {
            val tenantMnemonic = if (tenantName == "default") defaultMnemonic else tenantName
            updateConfig(tenantMnemonic, client, config)
        }
    }

    abstract fun updateConfig(tenantMnemonic: String, client: TenantRestClient, config: Map<String, String>)

    /**
     * Loads the tenant configs from all the channels.
     */
    fun loadTenantConfigs(): Map<String, Map<String, String>> {
        val allTenantConfigs = mutableMapOf<String, MutableMap<String, String>>()

        val configFiles = getConfigFiles()
        configFiles.forEach {
            val tenant = getTenant(it)
            val tenantConfigs = allTenantConfigs.computeIfAbsent(tenant) { mutableMapOf() }
            mergeConfigFromFile(it, tenantConfigs)
        }

        return allTenantConfigs
    }

    /**
     * Determines the tenant for the provided [file].
     */
    private fun getTenant(file: File): String = file.nameWithoutExtension

    /**
     * Merges the current [configs] with those found in the [file].
     * @throws RuntimeException if any duplicate keys are found.
     */
    private fun mergeConfigFromFile(file: File, configs: MutableMap<String, String>) {
        val properties = Properties()
        FileInputStream(file).use { properties.load(it) }

        properties.stringPropertyNames().forEach {
            if (configs.containsKey(it)) {
                throw IllegalStateException("Multiple configurations found for $it. Repeat found at ${file.path}")
            }

            configs[it] = properties.getProperty(it)
        }
    }

    /**
     * Gets the List of all config files associated to channels.
     */
    private fun getConfigFiles(): List<File> {
        val channelsDirectory = project.mirth().channel.baseDirectory.get()
        logger.lifecycle("Looking at $channelsDirectory")

        val configDirectories = channelsDirectory.asFile.listFiles()?.mapNotNull {
            if (it.isDirectory) {
                it.listFiles()?.find { c -> c.isDirectory && c.name == "tenant-server" }
                    ?.listFiles()?.find { c -> c.isDirectory && c.name == subfolder }
            } else {
                null
            }
        } ?: emptyList()
        return configDirectories.flatMap { it.listFiles().toList() }.filter { it.extension == "properties" }
    }
}
