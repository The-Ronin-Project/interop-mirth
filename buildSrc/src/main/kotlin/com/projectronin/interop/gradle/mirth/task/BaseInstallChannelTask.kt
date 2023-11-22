package com.projectronin.interop.gradle.mirth.task

import com.projectronin.interop.gradle.mirth.mirth
import com.projectronin.interop.gradle.mirth.rest.MirthRestClient.Companion.client
import io.ktor.http.isSuccess
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

abstract class BaseInstallChannelTask : BaseMirthTask() {
    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val xPathExpression = XPathFactory.newInstance().newXPath().compile("/channel/id")

    /**
     * Installs the [channelName] channel. Note that this will result in 2 channels within Mirth, one representing the
     * base channel and one representing a tenant-based channel that can be used for testing. Only the tenant-based
     * channel will be enabled.
     */
    protected fun installChannel(channelName: String) {
        logger.lifecycle("Installing channel $channelName")
        val channelExtension = project.mirth().channel

        val channelDirectory = channelExtension.generatedChannelDirectory.get()
        val channelFile = channelDirectory.file("$channelName.xml").asFile
        if (channelFile.exists()) {
            installBaseChannel(channelName, channelFile)
        } else {
            throw IllegalArgumentException("Provided channel is not a valid channel name in the current project")
        }
    }

    /**
     * Installs the base [channelName] channel as defined in [channelFile]. This Channel will be installed as-is and
     * will be disabled by default.
     */
    private fun installBaseChannel(channelName: String, channelFile: File) {
        val channelId = getChannelId(channelFile)
        val channelXml = channelFile.readLines().joinToString("\n")

        val status = client.putChannel(channelId, channelXml)
        if (status.isSuccess()) {
            logger.lifecycle("Successfully installed $channelName")
        } else {
            throw RuntimeException("Unsuccessful status code $status returned while installing $channelName")
        }

        client.disableChannel(channelId)
    }

    /**
     * Determines the channel ID for the [channelFile].
     */
    private fun getChannelId(channelFile: File): String = xPathExpression.evaluate(documentBuilder.parse(channelFile))
}
