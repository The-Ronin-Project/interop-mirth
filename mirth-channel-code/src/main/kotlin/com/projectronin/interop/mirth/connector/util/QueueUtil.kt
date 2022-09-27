package com.projectronin.interop.mirth.connector.util

import com.projectronin.interop.queue.db.DBQueueService
import com.projectronin.interop.queue.db.data.MessageDAO

internal object QueueUtil {
    private val messageDAO = MessageDAO(DatabaseUtil.queueDatabase)
    val queueService = DBQueueService(messageDAO)
}
