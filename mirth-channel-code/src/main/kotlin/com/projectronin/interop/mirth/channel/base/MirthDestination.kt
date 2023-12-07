package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse

interface MirthDestination {
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
        channelMap: Map<String, Any>,
    ): MirthFilterResponse

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
        channelMap: Map<String, Any>,
    ): MirthMessage

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
        channelMap: Map<String, Any>,
    ): MirthResponse
}
