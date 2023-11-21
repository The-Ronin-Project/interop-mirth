package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.models.MirthMessage
import com.projectronin.interop.mirth.models.filter.MirthFilter
import com.projectronin.interop.mirth.models.filter.MirthFilterResponse
import com.projectronin.interop.mirth.models.transformer.MirthTransformer
import com.projectronin.interop.tenant.config.exception.TenantMissingException
import mu.KotlinLogging

/**
 * Abstract Mirth channel service class.
 * Provides functions for a channel's
 * Source stages (Reader, Filter, Transformer), and
 * Scripts (Deploy, Undeploy, Preprocessor, Postprocessor).
 *
 * For details about the all required and optional Mirth channel stages, see [BaseService].
 *
 * Each Interops Mirth channel MUST override these functions in a [ChannelService] subclass:
 * - channelOnDeploy()
 * - channelSourceReader()
 *
 * Each Interops Mirth channel MAY override these functions in the same [ChannelService] subclass:
 * - channelOnUndeploy()
 * - channelOnPreprocessor()
 * - channelOnPostprocessor()
 * - channelSourceFilter()
 * - channelSourceTransformer()
 *
 * Each Interops Mirth channel MUST have at least one Destination and may have multiple Destinations.
 * The [ChannelService] MUST define a unique key to each of its [DestinationService] subclasses in this map:
 * - destinations
 */
abstract class ChannelService : MirthSource {
    protected val logger = KotlinLogging.logger(this::class.java.name)

    /**
     * Required: Mirth channels must call onDeploy() from the channel Deploy script.
     *
     * The Deploy script runs once, each time someone Deploys the channel. It does not run when the channel polls.
     * For Redeploy, the Undeploy script runs first, then the Deploy script; see [onUndeploy].
     *
     * The usual reason to Deploy or Redeploy a channel is to apply updates
     * to the channel definition or the Interop JAR file.
     *
     * Enabling, disabling, starting, and stopping channels are simpler and more common operations
     * that do not have corresponding scripts that run. For clarification, see the Mirth user guide:
     * https://www.nextgen.com/-/media/files/nextgen-connect/nextgen-connect-311-user-guide.pdf
     * or the corresponding guide for the Mirth version in use.
     *
     * Previous channel stage: (None).
     *
     * Next channel stage: Source Reader is the first stage to run, each time the channel polls.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap an optional map of values to be used during the Deploy stage. The map is needed only when Mirth
     *      needs to pass data to Kotlin to successfully deploy the channel; this is not expected in the usual case.
     * @return a map of values to be used during later channel stages.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     */
    override fun onDeploy(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        require(rootName.length <= 31) { "Channel root name length is over the limit of 31" }
        require(deployedChannelName.length <= 40) { "Deployed channel name length is over the limit of 40" }
        val tenantMap = addTenantToServiceMap(deployedChannelName, serviceMap)
        try {
            return channelOnDeploy(tenantMap[MirthKey.TENANT_MNEMONIC.code] as String, tenantMap)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during onDeploy: ${e.message}" }
            throw e
        }
    }

    /**
     * Required: [ChannelService] subclasses must override channelOnDeploy() to execute actions for onDeploy().
     *
     * Previous channel stage: (None).
     *
     * Next channel stage: Source Reader is the first stage to run, each time the channel polls.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param serviceMap expect [onDeploy] to pass in the serviceMap.
     * @return a map of values to be used during later channel stages.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     */
    open fun channelOnDeploy(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> = serviceMap

    /**
     * Mirth channels must call onUndeploy() from the Undeploy script, if there is an Undeploy script on the channel.
     *
     * The Undeploy script runs once, each time someone Undeploys or Redeploys the channel.
     * For Redeploy, the Undeploy script runs first, then the Deploy script; see [onDeploy]
     *
     * The usual reason to deploy or redeploy a channel is to apply updates
     * to the channel definition or the Interop JAR file.
     *
     * Enabling, disabling, starting, and stopping channels are simpler and more common operations
     * that do not have corresponding scripts that run. For clarification, see the Mirth user guide:
     * https://www.nextgen.com/-/media/files/nextgen-connect/nextgen-connect-311-user-guide.pdf
     * or the corresponding guide for the Mirth version in use.
     *
     * Previous channel stage: (None).
     *
     * Next channel stage: (None).
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap a map of values to be used during the Undeploy stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a map of values.
     */
    fun onUndeploy(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        val tenantMap = addTenantToServiceMap(deployedChannelName, serviceMap)
        val tenantMnemonic = tenantMap[MirthKey.TENANT_MNEMONIC.code] as String
        try {
            return channelOnUndeploy(tenantMnemonic, tenantMap)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during onUndeploy: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelOnUndeploy() to execute actions for onUndeploy()
     * if the Mirth channel has an Undeploy script; otherwise omit it.
     *
     * Previous channel stage: (None).
     *
     * Next channel stage: (None).
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param serviceMap expect [onUndeploy] to pass in the serviceMap.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a map of values.
     */
    open fun channelOnUndeploy(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> {
        return emptyMap()
    }

    /**
     * Mirth channels must call onPreprocessor() from the Preprocessor script, if there is a Preprocessor script on the channel.
     *
     * Previous channel stage: Source Reader.
     *
     * Next channel stage: Source Filter, Source Transformer, or later stages.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap a map of values to be used during the Preprocessor stage.
     * @return a map of values to be used during later channel stages.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     */
    fun onPreprocessor(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        val tenantMap = addTenantToServiceMap(deployedChannelName, serviceMap)
        try {
            return channelOnPreprocessor(tenantMap[MirthKey.TENANT_MNEMONIC.code] as String, tenantMap)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during onPreprocessor: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelOnPreprocessor() to execute actions for onPreprocessor(),
     * if the Mirth channel has a Preprocessor script; otherwise omit it.
     *
     * Previous channel stage: Source Reader.
     *
     * Next channel stage: Source Filter, Source Transformer, or later stages.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param serviceMap expect [onPreprocessor] to pass in the serviceMap.
     * @return a map of values to be used during later channel stages.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     */
    open fun channelOnPreprocessor(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> {
        return emptyMap()
    }

    /**
     * Mirth channels call onPreprocessor() from the Postprocessor script, if there is a Postprocessor script on the channel.
     *
     * Previous channel stage: last stage in the channel design; for order of available stages see [MirthChannelStage].
     *
     * Next channel stage: (None).
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap a map of values to be used during the Postprocessor stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a map of values.
     */
    fun onPostprocessor(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        val tenantMap = addTenantToServiceMap(deployedChannelName, serviceMap)
        try {
            return channelOnPostprocessor(tenantMap[MirthKey.TENANT_MNEMONIC.code] as String, tenantMap)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during onPostprocessor: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelOnPostprocessor() to execute actions for onPostprocessor()
     * if the channel has a Postprocessor script; otherwise omit it.
     *
     * Previous channel stage: last stage in the channel design; for order of available stages see [MirthChannelStage].
     *
     * Next channel stage: (None).
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param serviceMap expect [onPostprocessor] to pass in the serviceMap.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a map of values.
     */
    open fun channelOnPostprocessor(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> {
        return emptyMap()
    }

    /**
     * Required: Mirth channels call sourceReader() from the Source Reader script.
     *
     * Previous channel stage: Source Reader is the first stage to run, each time a deployed channel polls.
     *
     * Next channel stage: Preprocessor, Source Filter, or later stages.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap a map of values to be used during the Source Reader stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth message data to pass to the next channel stage.
     */
    override fun sourceReader(deployedChannelName: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        val tenantMap = addTenantToServiceMap(deployedChannelName, serviceMap)
        val tenantId = tenantMap[MirthKey.TENANT_MNEMONIC.code] as String
        try {
            return channelSourceReader(tenantId, tenantMap).inject(MirthKey.TENANT_MNEMONIC to tenantId)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during sourceReader: ${e.message}" }
            throw e
        }
    }

    /**
     * Required: [ChannelService] subclasses must override channelSourceReader() to execute actions for sourceReader().
     *
     * Previous channel stage: Source Reader is the first stage to run, each time a deployed channel polls.
     *
     * Next channel stage: Preprocessor, Source Filter, or later stages.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param serviceMap expect [sourceReader] to pass in the serviceMap.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth message data to pass to the next channel stage.
     */
    abstract fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage>

    /**
     * Mirth channels call sourceFilter() from the Source Filter script, if the channel has a Source Filter.
     *
     * Previous channel stage: Source Reader.
     *
     * Next channel stage: Source Transformer, or later stages.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap a map of values to be used during the Source Reader stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return true if the message should continue processing, false to stop processing the message.
     */
    override fun sourceFilter(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        val filter = getSourceFilter() ?: return MirthFilterResponse(true)

        val tenantMap = addTenantToServiceMap(deployedChannelName, channelMap)
        val tenantMnemonic = tenantMap[MirthKey.TENANT_MNEMONIC.code] as String
        try {
            return filter.filter(tenantMnemonic, msg, sourceMap, channelMap)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during sourceFilter: ${e.message}" }
            throw e
        }
    }

    override fun getSourceFilter(): MirthFilter? = null

    /**
     * Mirth channels call sourceTransformer() from the Source Transformer script, if the channel has a Source Transformer.
     *
     * Previous channel stage: Source Filter, or earlier stages.
     *
     * Next channel stage: one of the Destination stages, i.e. Destination Writer.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap a map of values to be used during the Source Transformer stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a Mirth message to pass to the next channel stage.
     */
    override fun sourceTransformer(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val transformer = getSourceTransformer() ?: return MirthMessage(msg)

        val tenantMap = addTenantToServiceMap(deployedChannelName, channelMap)
        val tenantMnemonic = tenantMap[MirthKey.TENANT_MNEMONIC.code]!! as String
        try {
            return transformer.transform(
                tenantMnemonic,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during sourceTransformer: ${e.message}" }
            throw e
        }
    }

    override fun getSourceTransformer(): MirthTransformer? = null

    /**
     * If the tenant mnemonic value is not already present in the input serviceMap,
     * extract the tenant mnemonic string from the deployedChannelName and add it to the serviceMap.
     *
     * @return copy of serviceMap with the tenant mnemonic value at the key "tenantMnemonic".
     * @throws TenantMissingException if no tenant mnemonic can be found.
     */
    protected fun addTenantToServiceMap(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
        return if (serviceMap.containsKey(MirthKey.TENANT_MNEMONIC.code)) {
            serviceMap
        } else {
            mapOf(MirthKey.TENANT_MNEMONIC.code to getTenantNameFromDeployedChannelName(deployedChannelName)) + serviceMap
        }
    }

    /**
     * Extract the tenant mnemonic string from the deployed channel name string.
     * Example: deployedChannelName "MDAOC-PractitionerLoad" and
     * the channel rootName "PractitionerLoad"
     * return the tenant mnemonic "mdaoc".
     *
     * @return tenant mnemonic value.
     * @throws TenantMissingException if no tenant mnemonic can be parsed from the name.
     */
    protected fun getTenantNameFromDeployedChannelName(deployedChannelName: String): String {
        if ((rootName.isEmpty()) || (deployedChannelName == rootName)) {
            throw TenantMissingException()
        }
        val index = deployedChannelName.indexOf("-$rootName")
        return if (index > 0) deployedChannelName.substring(0, index).lowercase() else throw TenantMissingException()
    }

    /**
     * Injects the supplied key-value pairs into the data maps for each message in this List.
     */
    private fun List<MirthMessage>.inject(vararg keyedValues: Pair<MirthKey, String>): List<MirthMessage> {
        val newValues = keyedValues.associate { it.first.code to it.second }
        return map { MirthMessage(it.message, it.dataMap + newValues) }
    }
}
