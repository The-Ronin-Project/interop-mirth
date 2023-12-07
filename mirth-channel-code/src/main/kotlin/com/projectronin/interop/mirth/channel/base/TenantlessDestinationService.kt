package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import mu.KotlinLogging

abstract class TenantlessDestinationService : MirthDestination {
    protected val logger = KotlinLogging.logger(this::class.java.name)

    override fun destinationFilter(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthFilterResponse {
        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code]!! as String

        try {
            return channelDestinationFilter(
                tenantMnemonic,
                msg,
                sourceMap,
                channelMap,
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationFilter: ${e.message}" }
            throw e
        }
    }

    /**
     * [TenantlessDestinationService] subclasses must override channelDestinationFilter() to execute actions for destinationFilter()
     * if there is a Filter on this Destination; otherwise omit it.
     *
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
        channelMap: Map<String, Any>,
    ): MirthFilterResponse {
        return MirthFilterResponse(true)
    }

    override fun destinationTransformer(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthMessage {
        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code]!! as String
        try {
            return channelDestinationTransformer(
                tenantMnemonic,
                msg,
                sourceMap,
                channelMap,
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationTransformer: ${e.message}" }
            throw e
        }
    }

    /**
     * [TenantlessDestinationService] subclasses must override channelDestinationTransformer() to execute actions for destinationTransformer()
     * if there is a Transformer on this Destination; otherwise omit it.
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
        channelMap: Map<String, Any>,
    ): MirthMessage {
        return MirthMessage(msg)
    }

    override fun destinationWriter(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthResponse {
        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code]!! as String
        try {
            return channelDestinationWriter(
                tenantMnemonic,
                msg,
                sourceMap,
                channelMap,
            )
        } catch (e: Throwable) {
            logger.error(e) { "Exception encountered during destinationWriter: ${e.message}" }
            throw e
        }
    }

    /**
     * Required: [TenantlessDestinationService] subclasses must override channelDestinationWriter() to execute actions for destinationWriter().
     *
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
        channelMap: Map<String, Any>,
    ): MirthResponse
}
