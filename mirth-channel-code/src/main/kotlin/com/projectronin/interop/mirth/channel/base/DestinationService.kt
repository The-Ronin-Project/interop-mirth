package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.models.MirthMessage
import com.projectronin.interop.mirth.models.filter.MirthFilterResponse
import mu.KotlinLogging

/**
 * Abstract Mirth destination service class.
 * Provides functions for a channel's
 * Destination stages (Writer, Filter, Transformer).
 *
 * For clarification, see the Mirth user guide:
 * https://www.nextgen.com/-/media/files/nextgen-connect/nextgen-connect-311-user-guide.pdf
 * or the corresponding guide for the Mirth version in use.
 * See "The Message Processing Lifecycle" under "The Fundamentals of Mirth Connect".
 *
 * Each destination in each Mirth channel in Interop MUST override this function in a [DestinationService] subclass:
 * - channelDestinationWriter()
 *
 * Each destination in each Mirth channel in Interop MAY also override these functions in the same [DestinationService] subclass:
 * - channelDestinationFilter()
 * - channelDestinationTransformer()
 *
 * The [ChannelService] sets the rootName for each of its [DestinationService] subclasses
 * when it populates the members of its destinations list.
 *
 * For the correct order of execution of all required and optional Mirth channel stages, see [BaseService].
 */
abstract class DestinationService {
    protected val logger = KotlinLogging.logger(this::class.java.name)

    /**
     * Mirth channels call destinationFilter() from the Destination Filter script,
     * if there is a Filter on this Destination.
     *
     * Previous channel stage: Source Transformer, or earlier stages.
     *
     * Next channel stage: Destination Transformer, or later stages.
     *
     * @param unusedValue Deprecated, unused field that Mirth is providing.
     * @param msg a string value from Mirth.
     * @param sourceMap the sourceMap from Mirth, including data collected in the serviceMap from earlier stages.
     * @param channelMap the channelMap from Mirth
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return true if the message should continue processing, false to stop processing the message.
     */
    fun destinationFilter(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        try {
            return channelDestinationFilter(
                sourceMap[MirthKey.TENANT_MNEMONIC.code] as String,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationFilter: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelDestinationFilter() to execute actions for destinationFilter()
     * if there is a Filter on this Destination; otherwise omit it.
     *
     * Previous channel stage: Source Transformer, or earlier stages.
     *
     * Next channel stage: Destination Transformer, or later stages.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param msg expect [destinationFilter] to pass in the msg.
     * @param sourceMap expect [destinationFilter] to pass in the sourceMap.
     * @param channelMap expect [destinationFilter] to pass in the channelMap.
     * @return true if the message should continue processing, false to stop processing the message.
     */
    open fun channelDestinationFilter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse {
        return MirthFilterResponse(true)
    }

    /**
     * Mirth channels call destinationTransformer() from the Destination Transformer script,
     * if there is a Transformer on this Destination.
     *
     * Previous channel stage: Destination Filter, or earlier stages.
     *
     * Next channel stage: Destination Writer.
     *
     * @param unusedValue Deprecated, unused field that Mirth is providing.
     * @param msg a string value from Mirth.
     * @param sourceMap the sourceMap from Mirth, including data collected in the serviceMap from earlier stages.
     * @param channelMap the channelMap from Mirth
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    fun destinationTransformer(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        try {
            return channelDestinationTransformer(
                sourceMap[MirthKey.TENANT_MNEMONIC.code] as String,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationTransformer: ${e.message}" }
            throw e
        }
    }

    /**
     * [ChannelService] subclasses must override channelDestinationTransformer() to execute actions for destinationTransformer()
     * if there is a Transformer on this Destination; otherwise omit it.
     *
     * Previous channel stage: Destination Filter, or earlier stages.
     *
     * Next channel stage: Destination Writer.
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param msg expect [destinationTransformer] to pass in the msg.
     * @param sourceMap expect [destinationTransformer] to pass in the sourceMap.
     * @param channelMap expect [destinationTransformer] to pass in the channelMap.
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    open fun channelDestinationTransformer(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage {
        return MirthMessage(msg)
    }

    /**
     * Required. Mirth channels call destinationWriter() from each Destination Writer script.
     *
     * Previous channel stage: Destination Transformer, or earlier stages.
     *
     * Next channel stage: Response Transformer, Postprocessor, or (None).
     *
     * @param unusedValue Deprecated, unused field that Mirth is providing.
     * @param msg a string value from Mirth.
     * @param sourceMap the sourceMap from Mirth, including data collected in the serviceMap from earlier stages.
     * @param channelMap the channelMap from Mirth
     *      Map keys: For conventions and a few reserved values see [BaseService].
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    fun destinationWriter(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        try {
            return channelDestinationWriter(
                sourceMap[MirthKey.TENANT_MNEMONIC.code] as String,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationWriter: ${e.message}" }
            throw e
        }
    }

    /**
     * Required: [DestinationService] subclasses must override channelDestinationWriter() to execute actions for destinationWriter().
     *
     * Previous channel stage: Destination Transformer, or earlier stages.
     *
     * Next channel stage: Response Transformer, Postprocessor, or (None).
     *
     * @param tenantMnemonic expect the correct value to be supplied.
     * @param msg expect [destinationWriter] to pass in the msg.
     * @param sourceMap expect [destinationWriter] to pass in the sourceMap.
     * @param channelMap expect [destinationWriter] to pass in the channelMap.
     * @return a list of Mirth response data to pass to the next channel stage.
     */
    abstract fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse
}
