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
private const val CHANNEL_ROOT_NAME = "TestChannelService"
private const val VALID_DEPLOYED_NAME = "$VALID_TENANT_ID-$CHANNEL_ROOT_NAME"

/**
 * Unit tests for functions in the [DestinationService] contract that are not currently overridden in any subclass.
 */
class DestinationServiceTest {
    @Test
    fun `destinationFilter - non-empty map`() {
        val serviceMap = mapOf<String, Any>(
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )
        assertTrue(
            TestDestinationService(CHANNEL_ROOT_NAME).destinationFilter(
                VALID_DEPLOYED_NAME,
                "",
                serviceMap,
                serviceMap
            ).result
        )
    }

    @Test
    fun `destinationFilter - tenant ok, empty map`() {
        val serviceMap = emptyMap<String, Any>()
        assertTrue(
            TestDestinationService(CHANNEL_ROOT_NAME).destinationFilter(
                VALID_DEPLOYED_NAME,
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

        val service = object : DestinationService(CHANNEL_ROOT_NAME, mockk()) {
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
                VALID_DEPLOYED_NAME,
                "",
                emptyMap(),
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
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )
        val actualMessage = TestDestinationService(CHANNEL_ROOT_NAME).destinationTransformer(
            VALID_DEPLOYED_NAME,
            "",
            serviceMap,
            serviceMap
        )
        assertEquals(emptyMirthMessage(), actualMessage)
    }

    @Test
    fun `destinationTransformer - tenant ok, empty map`() {
        val serviceMap = emptyMap<String, Any>()
        val actualMessage = TestDestinationService(CHANNEL_ROOT_NAME).destinationTransformer(
            VALID_DEPLOYED_NAME,
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

        val service = object : DestinationService(CHANNEL_ROOT_NAME, mockk()) {
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
                VALID_DEPLOYED_NAME,
                "",
                emptyMap(),
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
            MirthKey.TENANT_MNEMONIC.code to VALID_TENANT_ID,
        )
        val actualMessage = TestDestinationService(CHANNEL_ROOT_NAME).destinationWriter(
            VALID_DEPLOYED_NAME,
            "",
            serviceMap,
            serviceMap
        )
        val expectedResponse = (MirthResponse(MirthResponseStatus.SENT, "", ""))
        assertEquals(expectedResponse, actualMessage)
    }

    @Test
    fun `destinationWriter - tenant ok, empty map`() {
        val response = TestDestinationService(CHANNEL_ROOT_NAME).destinationWriter(
            VALID_DEPLOYED_NAME,
            "",
            emptyMap(),
            emptyMap()
        )
        val expectedResponse = (MirthResponse(MirthResponseStatus.SENT, "", ""))
        assertEquals(expectedResponse, response)
    }

    @Test
    fun `destinationWriter - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : DestinationService(CHANNEL_ROOT_NAME, mockk()) {
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
                VALID_DEPLOYED_NAME,
                "",
                emptyMap(),
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

class TestDestinationService(rootName: String) : DestinationService(rootName, mockk()) {
    override fun channelDestinationWriter(
        tenantMnemonic: String,
        msg: String,
        sourceMap: Map<String, Any>,
        channelMap: Map<String, Any>
    ): MirthResponse {
        return MirthResponse(MirthResponseStatus.SENT)
    }
}
