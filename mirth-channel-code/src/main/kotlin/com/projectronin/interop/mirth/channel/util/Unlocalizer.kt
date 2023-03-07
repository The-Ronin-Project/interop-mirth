package com.projectronin.interop.mirth.channel.util

import com.projectronin.interop.tenant.config.model.Tenant

fun String.unlocalize(tenant: Tenant) = this.removePrefix("${tenant.mnemonic}-")
