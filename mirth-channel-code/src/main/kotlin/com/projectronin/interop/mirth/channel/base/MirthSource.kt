package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage

interface MirthSource {
    /**
     * rootName is the tenant agnostic channel name as archived in source control.
     * Example: "PractitionerLoad".
     **
     * Example: the deployed channel name "MDAOC-PractitionerLoad" in Mirth
     * corresponds to the [MirthSource] rootName "PractitionerLoad"
     * for the "mdaoc" tenant mnemonic.
     */
    val rootName: String

    /**
     * Mirth channels may have multiple Destination Writers. They must have at least one.
     *
     * Each Destination Writer has its own [DestinationService] subclass to define its functions,
     * including its optional Destination Filter and Destination Transformer stages.
     *
     * An [MirthSource] must set the rootName and map key for each of its [MirthDestination] subclasses
     * when it populates the members of its destinations list. The map key may be any String.
     *
     * Mirth channels invoke [MirthDestination] functions as follows.
     * Suppose the key for the [MirthDestination] is "publish" in the
     * [MirthSource] destinations map. In the Mirth channel code, the Filter script for that Destination may call:
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
    val destinations: Map<String, MirthDestination>

    /**
     * Some channels may need to use non-Javascript destinations. Such channels should provide the configuration for
     * those destinations here in order to allow us to generate the appropriate destinations.
     */
    fun getNonJavascriptDestinations(): List<DestinationConfiguration> = emptyList()

    /**
     * Required: Mirth channels must call onDeploy() from the channel Deploy script.
     *
     * The Deploy script runs once, each time someone Deploys the channel.
     **
     *
     * Next channel stage: Source Reader is the first stage to run, each time the channel polls.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap an optional map of values to be used during the Deploy stage. The map is needed only when Mirth
     *      needs to pass data to Kotlin to successfully deploy the channel; this is not expected in the usual case.
     * @return a map of values to be used during later channel stages.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     */
    fun onDeploy(deployedChannelName: String, serviceMap: Map<String, Any>): Map<String, Any>

    /**
     * Required: Mirth channels call sourceReader() from the Source Reader script.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param serviceMap a map of values to be used during the Source Reader stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth message data to pass to the next channel stage.
     */
    fun sourceReader(deployedChannelName: String, serviceMap: Map<String, Any>): List<MirthMessage>

    fun getSourceFilter(): MirthFilter?

    /**
     * Mirth channels call sourceFilter() from the Source Filter script.
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param sourceMap a map of values to be used during the Source Reader stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return true if the message should continue processing, false to stop processing the message.
     */
    fun sourceFilter(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        val filter = getSourceFilter() ?: return MirthFilterResponse(true)

        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code]!! as String
        try {
            return filter.filter(tenantMnemonic, msg, sourceMap, channelMap)
        } catch (e: Throwable) {
            throw e
        }
    }

    fun getSourceTransformer(): MirthTransformer?

    /**
     * Mirth channels call sourceTransformer() from the Source Transformer script
     *
     * @param deployedChannelName pass in the Mirth global variable called channelName.
     * @param sourceMap a map of values to be used during the Source Transformer stage.
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a Mirth message to pass to the next channel stage.
     */
    fun sourceTransformer(
        deployedChannelName: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        val transformer = getSourceTransformer() ?: return MirthMessage(msg)

        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code]!! as String
        try {
            return transformer.transform(
                tenantMnemonic,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            throw e
        }
    }
}
