package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.model.MirthMessage

interface MirthTransformer {
    fun transform(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthMessage
}
