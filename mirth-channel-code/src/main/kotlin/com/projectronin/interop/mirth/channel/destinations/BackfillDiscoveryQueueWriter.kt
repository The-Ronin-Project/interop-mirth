package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.backfill.client.QueueClient
import com.projectronin.interop.backfill.client.generated.models.NewQueueEntry
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.mirth.channel.base.TenantlessDestinationService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BackfillDiscoveryQueueWriter(val backfillQueueClient: QueueClient) : TenantlessDestinationService() {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>,
    ): MirthResponse {
        val references = JacksonUtil.readJsonList(msg, String::class)
        // no patients found for a given location, log an error and move on
        if (references.isEmpty()) {
            logger.warn { "No Patients found for tenant $tenantMnemonic" }
            return MirthResponse(
                MirthResponseStatus.SENT,
                "No Patients found for tenant $tenantMnemonic",
            )
        }

        return try {
            val backFillUUID = UUID.fromString(sourceMap[MirthKey.BACKFILL_ID.code] as String)
            val newQueueEntries =
                references.map {
                    NewQueueEntry(backFillUUID, it.substringAfter("/"))
                }
            val response =
                runBlocking {
                    backfillQueueClient.postQueueEntry(backFillUUID, newQueueEntries)
                }
            MirthResponse(
                MirthResponseStatus.SENT,
                JacksonUtil.writeJsonValue(response),
                "Queue entries successfully created",
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create new queue entries" }
            MirthResponse(
                MirthResponseStatus.ERROR,
                e.stackTraceToString(),
                e.message,
            )
        }
    }
}
