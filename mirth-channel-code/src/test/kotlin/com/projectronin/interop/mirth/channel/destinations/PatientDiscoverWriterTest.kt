package com.projectronin.interop.mirth.channel.destinations

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.kafka.KafkaLoadService
import com.projectronin.interop.kafka.model.DataTrigger
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PatientDiscoverWriterTest {
    lateinit var kafkaLoadService: KafkaLoadService
    lateinit var kafkaPushResponse: PushResponse<String>
    lateinit var writer: PatientDiscoveryWriter

    @BeforeEach
    fun setup() {
        kafkaLoadService = mockk()
        kafkaPushResponse = mockk() {
            every { successful } returns listOf("yes")
            every { failures } returns emptyList()
        }
        writer = PatientDiscoveryWriter(kafkaLoadService)
    }

    @Test
    fun `channelDestinationWriter  - works`() {
        every {
            kafkaLoadService.pushLoadEvent(
                "ronin",
                DataTrigger.NIGHTLY,
                listOf("123", "456"),
                ResourceType.PATIENT
            )
        } returns kafkaPushResponse

        val result = writer.channelDestinationWriter(
            "ronin",
            "[\"Patient/123\",\"Patient/456\"]",
            emptyMap(),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.SENT, result.status)
    }

    @Test
    fun `channelDestinationWriter  - catches error`() {
        every {
            kafkaLoadService.pushLoadEvent(
                "ronin",
                DataTrigger.NIGHTLY,
                listOf("123", "456"),
                ResourceType.PATIENT
            )
        } throws Exception("bad")
        val result = writer.channelDestinationWriter(
            "ronin",
            "[\"Patient/123\",\"Patient/456\"]",
            emptyMap(),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("bad", result.detailedMessage)
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

    @Test
    fun `channelDestinationWriter -  handles failures from kafka`() {
        every { kafkaPushResponse.successful } returns emptyList()
        every { kafkaPushResponse.failures } returns listOf(mockk("yes"))
        every {
            kafkaLoadService.pushLoadEvent(
                "ronin",
                DataTrigger.NIGHTLY,
                listOf("123"),
                ResourceType.PATIENT
            )
        } returns kafkaPushResponse
        val result = writer.channelDestinationWriter(
            "ronin",
            "[\"Patient/123\"]",
            emptyMap(),
            emptyMap()
        )
        assertEquals(MirthResponseStatus.ERROR, result.status)
        assertEquals("0 successes, 1 failures", result.message)
    }
}
