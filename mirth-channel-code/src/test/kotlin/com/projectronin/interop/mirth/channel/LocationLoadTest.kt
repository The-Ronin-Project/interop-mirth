package com.projectronin.interop.mirth.channel

import com.projectronin.event.interop.internal.v1.ResourceType
import com.projectronin.interop.kafka.model.PushResponse
import com.projectronin.interop.mirth.channel.base.kafka.completeness.KafkaDagPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocationLoadTest {
    private lateinit var channel: LocationLoad

    @BeforeEach
    fun setup() {
        channel = LocationLoad(mockk(), mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun `channel deploy publishes DAG`() {
        val kafkaDagPublisher: KafkaDagPublisher =
            mockk {
                every { publishDag(any(), any()) } returns PushResponse()
            }
        val channel =
            LocationLoad(
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                kafkaDagPublisher,
            )
        channel.onDeploy(channel.rootName, emptyMap())

        verify {
            kafkaDagPublisher.publishDag(
                withArg { resourceType ->
                    assertEquals(ResourceType.Location, resourceType)
                },
                withArg { consumedResources ->
                    assertEquals(consumedResources.size, 2)
                    Assertions.assertTrue(consumedResources.contains(ResourceType.Appointment))
                    Assertions.assertTrue(consumedResources.contains(ResourceType.Encounter))
                },
            )
        }
    }

    @Test
    fun `channel creation works`() {
        assertEquals("LocationLoad", channel.rootName)
        assertEquals("interop-mirth-location_group", channel.channelGroupId)
        assertEquals(1, channel.destinations.size)
    }
}
