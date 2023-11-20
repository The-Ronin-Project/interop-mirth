package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.model.MirthFilterResponse

interface MirthFilter {
    fun filter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthFilterResponse
}
