package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.models.filter.MirthFilter
import com.projectronin.interop.mirth.models.transformer.MirthTransformer
import mu.KotlinLogging

abstract class TenantlessDestinationService : MirthDestination {
    protected val logger = KotlinLogging.logger(this::class.java.name)

    override fun getFilter(): MirthFilter? = null
    override fun getTransformer(): MirthTransformer? = null

    override fun destinationWriter(
        unusedValue: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        val tenantMnemonic = sourceMap[MirthKey.TENANT_MNEMONIC.code]!! as String
        try {
            return channelDestinationWriter(
                tenantMnemonic,
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
        channelMap: Map<String, Any>
    ): MirthResponse
}
