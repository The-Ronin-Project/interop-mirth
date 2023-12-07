package com.projectronin.interop.mirth.channel.model

import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus

data class MirthResponse(
    val status: MirthResponseStatus,
    val detailedMessage: String? = "",
    val message: String? = "",
    val dataMap: Map<String, Any> = emptyMap(),
)
