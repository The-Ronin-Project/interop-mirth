package com.projectronin.interop.mirth.channel

import com.projectronin.interop.kafka.KafkaPatientOnboardService
import com.projectronin.interop.kafka.PatientOnboardingStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OnboardFlagServiceTest {
    private lateinit var channel: OnboardFlagService
    private val patientOnboardService: KafkaPatientOnboardService = mockk()

    @BeforeEach
    fun setup() {
        channel = OnboardFlagService(patientOnboardService, mockk())
    }

    @Test
    fun `channel creation works`() {
        assertEquals("OnboardFlag", channel.rootName)
        assertEquals(1, channel.destinations.size)
    }

    @Test
    fun `source reader works`() {
        every { patientOnboardService.retrieveOnboardEvents(any()) } returns
            listOf(
                mockk(relaxed = true) {
                    every { action } returns PatientOnboardingStatus.OnboardAction.ONBOARD
                    every { patientId } returns "12345"
                    every { tenantId } returns "tenant"
                },
                mockk {
                    every { action } returns PatientOnboardingStatus.OnboardAction.OFFBOARD
                },
            )
        val result = channel.channelSourceReader(emptyMap())
        assertEquals("{\"patientId\":\"12345\",\"tenantId\":\"tenant\",\"action\":\"ONBOARD\"}", result.first().message)
    }
}
