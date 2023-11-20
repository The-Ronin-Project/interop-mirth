package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.mirth.channel.model.MirthMessage
import mu.KotlinLogging

/**
 * Abstract Mirth channel service class.
 * Provides functions for a channel's
 * Source stages (Reader, Filter, Transformer), and
 * Scripts (Deploy, Undeploy, Preprocessor, Postprocessor).
 *
 * For details about the all required and optional Mirth channel stages, see [BaseService].
 *
 * Each Interops Mirth channel MUST override these functions in a [TenantlessSourceService] subclass:
 * - channelOnDeploy()
 * - channelSourceReader()
 *
 * Each Interops Mirth channel MAY override these functions in the same [TenantlessSourceService] subclass:
 * - channelOnUndeploy()
 * - channelOnPreprocessor()
 * - channelOnPostprocessor()
 * - channelSourceFilter()
 * - channelSourceTransformer()
 *
 * Each Interops Mirth channel MUST have at least one Destination and may have multiple Destinations.
 * The [TenantlessSourceService] MUST define a unique key to each of its [DestinationService] subclasses in this map:
 * - destinations
 */
abstract class TenantlessSourceService : MirthSource {
    protected val logger = KotlinLogging.logger(this::class.java.name)

    abstract override val destinations: Map<String, TenantlessDestinationService>

    override fun onDeploy(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        require(rootName.length <= 31) { "Channel root name length is over the limit of 31" }
        require(deployedChannelName.length <= 40) { "Deployed channel name length is over the limit of 40" }
        try {
            return channelOnDeploy(serviceMap)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during on deploy: ${e.message}" }
            throw e
        }
    }

    /**
     * Required: [TenantlessSourceService] subclasses must override channelOnDeploy() to execute actions for onDeploy().
     **
     * @param serviceMap expect [onDeploy] to pass in the serviceMap.
     * @return a map of values to be used during later channel stages.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     */
    open fun channelOnDeploy(serviceMap: Map<String, Any>): Map<String, Any> = serviceMap

    override fun sourceReader(deployedChannelName: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        try {
            val messages = channelSourceReader(serviceMap)
            messages.checkTenant()
            return messages
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during sourceReader: ${e.message}" }
            throw e
        }
    }

    /**
     * Required: [TenantlessSourceService] subclasses must override channelSourceReader() to execute actions for sourceReader().
     *
     *
     * @param serviceMap expect [sourceReader] to pass in the serviceMap.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth message data to pass to the next channel stage.
     */
    abstract fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage>

    override fun getSourceFilter(): MirthFilter? = null

    override fun getSourceTransformer(): MirthTransformer? = null

    private fun List<MirthMessage>.checkTenant() {
        this.forEach {
            if (!it.dataMap.containsKey(MirthKey.TENANT_MNEMONIC.code)) {
                throw MapVariableMissing("Message missing tenant mnemonic")
            }
        }
    }
}
