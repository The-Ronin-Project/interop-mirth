package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse

interface MirthDestination {
    fun getConfiguration(): DestinationConfiguration

    fun getFilter(): MirthFilter?

    /**
     * Mirth channels call destinationFilter() from the Destination Filter script
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
        val filter = getFilter() ?: return MirthFilterResponse(true)

        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code]!! as String

        try {
            return filter.filter(
                tenantMnemonic,
                msg,
                sourceMap,
                channelMap
            )
        } catch (e: Throwable) {
            throw e
        }
    }

    fun getTransformer(): MirthTransformer?

    /**
     * Mirth channels call destinationTransformer() from the Destination Transformer script
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
        val transformer = getTransformer() ?: return MirthMessage(msg)

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

    /**
     * Required. Mirth channels call destinationWriter() from each Destination Writer script.
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
    ): MirthResponse
}
