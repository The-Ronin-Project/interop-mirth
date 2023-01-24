package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.fhir.ronin.TransformManager
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.exception.TenantMissingException

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
abstract class ChannelService(tenantService: TenantService, transformManager: TransformManager) :
    BaseService(tenantService, transformManager) {
    /**
     * rootName is the tenant agnostic channel name as archived in source control.
     * Example: "PractitionerLoad".
     *
     * Each deployed channel in Mirth prefixes a tenant mnemonic and hyphen to this rootName.
     * The tenant mnemonic is the lowercase string defined for each Ronin customer in the Ronin tenant ID list.
     *
     * Example: the deployed channel name "MDAOC-PractitionerLoad" in Mirth
     * corresponds to the [ChannelService] rootName "PractitionerLoad"
     * for the "mdaoc" tenant mnemonic.
     */
    abstract val rootName: String

    /**
     * Mirth channels may have multiple Destination Writers. They must have at least one.
     *
     * Each Destination Writer has its own [DestinationService] subclass to define its functions,
     * including its optional Destination Filter and Destination Transformer stages.
     *
     * A [ChannelService] must set the rootName and map key for each of its [DestinationService] subclasses
     * when it populates the members of its destinations list. The map key may be any String.
     *
     * Mirth channels invoke [DestinationService] functions as follows.
     * Suppose the key for the [DestinationService] is "publish" in the
     * [ChannelService] destinations map. In the Mirth channel code, the Filter script for that Destination may call:
     *
     * ```
     * $gc("channelService").destinations.get("publish").destinationFilter(src, sourceMap, channelMap)
     * ```
     *
     * In the Mirth channel code, the Writer script for that Destination may call:
     *
     * ```
     * $gc("channelService").destinations.get("publish").destinationWriter(src, sourceMap, channelMap)
     * ```
     */
    abstract val destinations: Map<String, DestinationService>

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
    fun onDeploy(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any> {
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
    fun sourceReader(deployedChannelName: String, serviceMap: Map<String, Any>): List<MirthMessage> {
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
    open fun sourceFilter(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        val tenantMap = addTenantToServiceMap(deployedChannelName, channelMap)
        try {
            return channelSourceFilter(tenantMap[MirthKey.TENANT_MNEMONIC.code] as String, msg, sourceMap, tenantMap)
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during sourceFilter: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelSourceFilter() to execute actions for sourceFilter()
     * if the channel has a Source Filter; otherwise omit it.
     *
     * Previous channel stage: Source Reader.
     *
     * Next channel stage: Source Transformer, or later stages.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param serviceMap expect [onDeploy] to pass in the serviceMap it receives.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return true if the message should continue processing, false to stop processing the message.
     */
    open fun channelSourceFilter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        return MirthFilterResponse(true)
    }

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
    open fun sourceTransformer(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val tenantMap = addTenantToServiceMap(deployedChannelName, channelMap)
        try {
            return channelSourceTransformer(
                tenantMap[MirthKey.TENANT_MNEMONIC.code] as String,
                msg,
                sourceMap,
                tenantMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during sourceTransformer: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelSourceTransformer() to execute actions for sourceTransformer()
     * if the channel has a Source Transformer; otherwise omit it.
     *
     * Previous channel stage: Source Filter, or earlier stages.
     *
     * Next channel stage: one of the Destination stages, i.e. Destination Writer.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param serviceMap expect [onDeploy] to pass in the serviceMap it receives.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a Mirth message to pass to the next channel stage.
     */
    open fun channelSourceTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        return MirthMessage(msg)
    }

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
