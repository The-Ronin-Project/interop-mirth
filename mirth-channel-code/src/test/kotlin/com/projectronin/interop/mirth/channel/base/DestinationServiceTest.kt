package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.channel.model.emptyMirthMessage
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import mu.KLogger
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val VALID_TENANT_ID = "mdaoc"

/**
 * Unit tests for functions in the [DestinationService] contract that are not currently overridden in any subclass.
 */
class DestinationServiceTest {
    @Test
    fun `destinationFilter - non-empty map`() {
        val serviceMap = mapOf<String, Any>(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID
        )
        assertTrue(
            TestDestinationService().destinationFilter(
                "unused",
                "",
                serviceMap,
                serviceMap
            ).result
        )
    }

    @Test
    fun `destinationFilter - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : DestinationService(mockk(), mockk(), mockk()) {
            override fun channelDestinationFilter(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthFilterResponse {
                throw IllegalStateException("channelDestinationFilter exception")
            }

            override fun channelDestinationWriter(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthResponse {
                TODO("Not yet implemented")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.destinationFilter(
                "unused",
                "",
                mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID),
                emptyMap()
            )
        }
        assertEquals("channelDestinationFilter exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during destinationFilter: channelDestinationFilter exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `destinationTransformer - non-empty map`() {
        val serviceMap = mapOf<String, Any>(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID
        )
        val actualMessage = TestDestinationService().destinationTransformer(
            "unused",
            "",
            serviceMap,
            serviceMap
        )
        assertEquals(emptyMirthMessage(), actualMessage)
    }

    @Test
    fun `destinationTransformer - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : DestinationService(mockk(), mockk(), mockk()) {
            override fun channelDestinationTransformer(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthMessage {
                throw IllegalStateException("channelDestinationTransformer exception")
            }

            override fun channelDestinationWriter(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthResponse {
                TODO("Not yet implemented")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.destinationTransformer(
                "unused",
                "",
                mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID),
                emptyMap()
            )
        }
        assertEquals("channelDestinationTransformer exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during destinationTransformer: channelDestinationTransformer exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `destinationWriter - non-empty map`() {
        val serviceMap = mapOf<String, Any>(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID
        )
        val actualMessage = TestDestinationService().destinationWriter(
            "unused",
            "",
            serviceMap,
            serviceMap
        )
        val expectedResponse = (MirthResponse(MirthResponseStatus.SENT, "", ""))
        assertEquals(expectedResponse, actualMessage)
    }

    @Test
    fun `destinationWriter - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : DestinationService(mockk(), mockk(), mockk()) {
            override fun channelDestinationWriter(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthResponse {
                throw IllegalStateException("channelDestinationWriter exception")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.destinationWriter(
                "unused",
                "",
                mapOf<String, Any>("tenantMnemonic" to VALID_TENANT_ID),
                emptyMap()
            )
        }
        assertEquals("channelDestinationWriter exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during destinationWriter: channelDestinationWriter exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }
}

class TestDestinationService() : DestinationService(mockk(), mockk(), mockk()) {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        return MirthResponse(MirthResponseStatus.SENT)
    }
}
