package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.backfill.client.QueueClient
import com.projectronin.interop.backfill.client.generated.models.GeneratedId
import com.projectronin.interop.backfill.client.generated.models.NewQueueEntry
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class BackfillDiscoveryQueueWriterTest {
    lateinit var client: QueueClient
    lateinit var writer: BackfillDiscoveryQueueWriter
    val response = listOf(GeneratedId(UUID.randomUUID()))
    val backfillID = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        client = mockk()
        writer = BackfillDiscoveryQueueWriter(client)
    }

    @Test
    fun `channelDestinationWriter  - works`() {
        coEvery {
            client.postQueueEntry(
                backfillID,
                listOf(
                    NewQueueEntry(backfillID, "123"),
                    NewQueueEntry(backfillID, "456")
                )
            )
        } returns response

        val result = writer.channelDestinationWriter(
            "ronin",
            "[\"Patient/123\",\"Patient/456\"]",
            mapOf(MirthKey.BACKFILL_ID.code to backfillID.toString()),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
    }

    @Test
    fun `channelDestinationWriter  - catches error`() {
        coEvery {
            client.postQueueEntry(
                backfillID,
                listOf(
                    NewQueueEntry(backfillID, "123"),
                    NewQueueEntry(backfillID, "456")
                )
            )
        } throws Exception("bad")
        val result = writer.channelDestinationWriter(
            "ronin",
            "[\"Patient/123\",\"Patient/456\"]",
            mapOf(MirthKey.BACKFILL_ID.code to backfillID.toString()),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("bad", result.message)
        assertEquals("java.lang.Exception: bad", result.detailedMessage?.substring(0, 24))
    }

    @Test
    fun `channelDestinationWriter -  handles no patients with error`() {
        val result = writer.channelDestinationWriter(
            "ronin",
            "[]",
            emptyMap(),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("No Patients found for tenant ronin", result.detailedMessage)
    }
}
