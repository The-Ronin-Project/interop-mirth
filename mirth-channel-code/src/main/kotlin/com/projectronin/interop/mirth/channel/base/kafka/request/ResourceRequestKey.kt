package com.projectronin.interop.mirth.channel.base.kafka.request

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.mirth.channel.util.unlocalize
import com.projectronin.interop.tenant.config.model.Tenant
import java.time.OffsetDateTime
import java.util.Objects

data class ResourceRequestKey(
    val runId: String,
    val resourceType: ResourceType,
    val tenant: Tenant,
    val resourceId: String,
    val dateRange: Pair<OffsetDateTime, OffsetDateTime>? = null
) {
    // This is to ensure we have a consistent ID to base indexing off of.
    internal val unlocalizedResourceId = resourceId.unlocalize(tenant)

    override fun toString(): String = "$runId:$resourceType:$dateRange:${tenant.mnemonic}:$unlocalizedResourceId"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourceRequestKey
        return runId == other.runId &&
            resourceType == other.resourceType &&
            dateRange == other.dateRange &&
            tenant.mnemonic == other.tenant.mnemonic &&
            unlocalizedResourceId == other.unlocalizedResourceId
    }

    override fun hashCode(): Int {
        return Objects.hash(runId, resourceType, tenant.mnemonic, dateRange, unlocalizedResourceId)
    }
}
