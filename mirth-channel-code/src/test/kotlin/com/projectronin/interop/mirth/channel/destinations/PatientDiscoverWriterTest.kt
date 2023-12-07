package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.event.interop.internal.v1.Metadata
import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.util.generateMetadata
import com.projectronin.interop.mirth.channel.util.serialize
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class PatientDiscoverWriterTest {
    lateinit var kafkaLoadService: KafkaLoadService
    lateinit var kafkaPushResponse: PushResponse<String>
    lateinit var writer: PatientDiscoveryWriter

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        kafkaPushResponse =
            mockk {
                every { successful } returns listOf("yes")
                every { failures } returns emptyList()
            }
        writer = PatientDiscoveryWriter(kafkaLoadService)
    }

    @Test
    fun `channelDestinationWriter  - works`() {
        val metadata = generateMetadata()
        every {
            kafkaLoadService.pushLoadEvent(
                "ronin",
                DataTrigger.NIGHTLY,
                listOf("123", "456"),
                ResourceType.Patient,
                metadata,
            )
        } returns kafkaPushResponse

        val result =
            writer.channelDestinationWriter(
                "ronin",
                "[\"Patient/123\",\"Patient/456\"]",
                mapOf(MirthKey.EVENT_METADATA.code to serialize(metadata)),
                emptyMap(),
            )
        assertEquals(MirthResponseStatus.SENT, result.status)
    }

    @Test
    fun `channelDestinationWriter  - backfill - works`() {
        val metadata =
            generateMetadata(
                backfillInfo =
                    Metadata.BackfillRequest(
                        backfillId = "123",
                        backfillStartDate = OffsetDateTime.now(),
                        backfillEndDate = OffsetDateTime.now(),
                    ),
            )
        every {
            kafkaLoadService.pushLoadEvent(
                "ronin",
                DataTrigger.BACKFILL,
                listOf("123"),
                ResourceType.Patient,
                any(),
            )
        } returns kafkaPushResponse

        val result =
            writer.channelDestinationWriter(
                "ronin",
                "[\"123\"]",
                mapOf(MirthKey.EVENT_METADATA.code to serialize(metadata)),
                emptyMap(),
            )
        assertEquals(MirthResponseStatus.SENT, result.status)
    }

    @Test
    fun `channelDestinationWriter  - catches error`() {
        val metadata = generateMetadata()
        every {
            kafkaLoadService.pushLoadEvent(
                "ronin",
                DataTrigger.NIGHTLY,
                listOf("123", "456"),
                ResourceType.Patient,
                metadata,
            )
        } throws Exception("bad")
        val result =
            writer.channelDestinationWriter(
                "ronin",
                "[\"Patient/123\",\"Patient/456\"]",
                mapOf(MirthKey.EVENT_METADATA.code to serialize(metadata)),
                emptyMap(),
            )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("bad", result.detailedMessage)
    }

    @Test
    fun `channelDestinationWriter -  handles no patients with error`() {
        val result =
            writer.channelDestinationWriter(
                "ronin",
                "[]",
                emptyMap(),
                emptyMap(),
            )
        assertEquals(MirthResponseStatus.SENT, result.status)
        assertEquals("No Patients found for tenant ronin", result.detailedMessage)
    }

    @Test
    fun `channelDestinationWriter -  handles failures from kafka`() {
        every { kafkaPushResponse.successful } returns emptyList()
        every { kafkaPushResponse.failures } returns listOf(mockk("yes"))
        val metadata = generateMetadata()
        every {
            kafkaLoadService.pushLoadEvent(
                "ronin",
                DataTrigger.NIGHTLY,
                listOf("123"),
                ResourceType.Patient,
                metadata,
            )
        } returns kafkaPushResponse
        val result =
            writer.channelDestinationWriter(
                "ronin",
                "[\"Patient/123\"]",
                mapOf(MirthKey.EVENT_METADATA.code to serialize(metadata)),
                emptyMap(),
            )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("0 successes, 1 failures", result.message)
    }
}
