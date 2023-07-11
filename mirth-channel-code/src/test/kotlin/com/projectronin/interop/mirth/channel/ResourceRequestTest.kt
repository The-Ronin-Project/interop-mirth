package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.resource.request.v1.InteropResourceRequestV1
import com.projectronin.interop.common.jackson.JacksonUtil
import com.projectronin.interop.kafka.KafkaRequestService
import com.projectronin.interop.mirth.channel.enums.MirthKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResourceRequestTest {
    lateinit var channel: ResourceRequest
    lateinit var kafkaRequestService: KafkaRequestService

    @BeforeEach
    fun setup() {
        kafkaRequestService = mockk()
        channel = ResourceRequest(kafkaRequestService, mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("ResourceRequest", channel.rootName)
        assertEquals("interop-mirth-resource_request_group", channel.groupId)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `channel sourceReader works`() {
        mockkObject(JacksonUtil)
        val mockEvent = mockk<InteropResourceRequestV1> {
            every { tenantId } returns "no way dude"
            every { resourceFHIRId } returns "123"
            every { resourceType } returns "Patient"
        }
        every { kafkaRequestService.retrieveRequestEvents("interop-mirth-resource_request_group") } returns listOf(
            mockEvent
        )
        every { JacksonUtil.writeJsonValue(mockEvent) } returns "mockEvent"
        val messages = channel.channelSourceReader(emptyMap())
        assertEquals(1, messages.size)
        assertEquals("mockEvent", messages.first().message)
        assertNotNull(messages[0].dataMap[MirthKey.FHIR_ID.code])
        assertNotNull(messages[0].dataMap[MirthKey.RESOURCE_TYPE.code])
        unmockkAll()
    }
}
