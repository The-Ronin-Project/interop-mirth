package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.tenant.config.exception.TenantMissingException
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
private const val TENANT_MNEMONIC = "tenantMnemonic"

/**
 * Unit tests for functions in the [ChannelService] contract that are not currently overridden in any subclass.
 */
class ChannelServiceTest {
    @Test
    fun `onDeploy - bad tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onDeploy(
                "unusable",
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onDeploy - root channel name too long in class`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<java.lang.IllegalArgumentException> {
            TestChannelServiceBadName().onDeploy(
                "n2345678-PatientByQuestionnaireLoad",
                emptyServiceMap
            )
        }
        assertEquals("Channel root name length is over the limit of 31", ex.message)
    }

    @Test
    fun `onDeploy - deployed channel name too long in Mirth`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<java.lang.IllegalArgumentException> {
            TestChannelService().onDeploy(
                "nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn-$CHANNEL_ROOT_NAME",
                emptyServiceMap
            )
        }
        assertEquals("Deployed channel name length is over the limit of 40", ex.message)
    }

    @Test
    fun `onDeploy - no tenant in channel name or map`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onDeploy(
                CHANNEL_ROOT_NAME,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onDeploy - tenant ok in map, valid result with tenant in map`() {
        val configurationMap = mapOf<String, Any>(
            TENANT_MNEMONIC to VALID_TENANT_ID
        )
        val result = TestChannelService().onDeploy(CHANNEL_ROOT_NAME, configurationMap)

        assertEquals(configurationMap, result)
    }

    @Test
    fun `onDeploy - tenant ok in channel name, empty configuration map`() {
        val configurationMap: Map<String, Any> = emptyMap()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onDeploy(CHANNEL_ROOT_NAME, configurationMap)
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onDeploy - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : ChannelService(mockk()) {
            override val rootName: String = CHANNEL_ROOT_NAME
            override val destinations: Map<String, DestinationService> = emptyMap()

            override fun channelOnDeploy(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> {
                throw IllegalStateException("channelOnDeploy exception")
            }

            override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
                TODO("Not yet implemented")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.onDeploy(CHANNEL_ROOT_NAME, mapOf(TENANT_MNEMONIC to VALID_TENANT_ID))
        }
        assertEquals("channelOnDeploy exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during onDeploy: channelOnDeploy exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `onUndeploy - bad tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onUndeploy(
                "unusable",
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onUndeploy - no tenant in channel name or map`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onUndeploy(
                CHANNEL_ROOT_NAME,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onUndeploy - tenant ok in map, valid empty result`() {
        val configurationMap = mapOf<String, Any>(
            TENANT_MNEMONIC to VALID_TENANT_ID
        )
        val channelMap = TestChannelService().onUndeploy(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        val expectedMap: Map<String, Any> = emptyMap()
        assertEquals(expectedMap, channelMap)
    }

    @Test
    fun `onUndeploy - tenant ok in channel name, valid empty result`() {
        val configurationMap: Map<String, Any> = emptyMap()
        val channelMap = TestChannelService().onUndeploy(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        val expectedMap: Map<String, Any> = emptyMap()
        assertEquals(expectedMap, channelMap)
    }

    @Test
    fun `onUndeploy - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : ChannelService(mockk()) {
            override val rootName: String = CHANNEL_ROOT_NAME
            override val destinations: Map<String, DestinationService> = emptyMap()

            override fun channelOnUndeploy(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> {
                throw IllegalStateException("channelOnUndeploy exception")
            }

            override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
                TODO("Not yet implemented")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.onUndeploy(
                VALID_DEPLOYED_NAME,
                emptyMap()
            )
        }
        assertEquals("channelOnUndeploy exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during onUndeploy: channelOnUndeploy exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `onPreprocessor - bad tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onPreprocessor(
                "unusable",
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onPreprocessor - no tenant in channel name or map`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onPreprocessor(
                CHANNEL_ROOT_NAME,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onPreprocessor - tenant ok in map, valid empty result`() {
        val configurationMap = mapOf<String, Any>(
            TENANT_MNEMONIC to VALID_TENANT_ID
        )
        val channelMap = TestChannelService().onPreprocessor(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        val expectedMap: Map<String, Any> = emptyMap()
        assertEquals(expectedMap, channelMap)
    }

    @Test
    fun `onPreprocessor - tenant ok in channel name, valid empty result`() {
        val configurationMap: Map<String, Any> = emptyMap()
        val channelMap = TestChannelService().onPreprocessor(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        val expectedMap: Map<String, Any> = emptyMap()
        assertEquals(expectedMap, channelMap)
    }

    @Test
    fun `onPreprocessor - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : ChannelService(mockk()) {
            override val rootName: String = CHANNEL_ROOT_NAME
            override val destinations: Map<String, DestinationService> = emptyMap()

            override fun channelOnPreprocessor(tenantMnemonic: String, serviceMap: Map<String, Any>): Map<String, Any> {
                throw IllegalStateException("channelOnPreprocessor exception")
            }

            override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
                TODO("Not yet implemented")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.onPreprocessor(
                VALID_DEPLOYED_NAME,
                emptyMap()
            )
        }
        assertEquals("channelOnPreprocessor exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during onPreprocessor: channelOnPreprocessor exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `onPostprocessor - bad tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onPostprocessor(
                "unusable",
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onPostprocessor - no tenant in channel name or map`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().onPostprocessor(
                CHANNEL_ROOT_NAME,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `onPostprocessor - tenant ok in map, valid empty result`() {
        val configurationMap = mapOf<String, Any>(
            TENANT_MNEMONIC to VALID_TENANT_ID
        )
        val channelMap = TestChannelService().onPostprocessor(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        val expectedMap: Map<String, Any> = emptyMap()
        assertEquals(expectedMap, channelMap)
    }

    @Test
    fun `onPostprocessor - tenant ok in channel name, valid empty result`() {
        val configurationMap: Map<String, Any> = emptyMap()
        val channelMap = TestChannelService().onPostprocessor(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        val expectedMap: Map<String, Any> = emptyMap()
        assertEquals(expectedMap, channelMap)
    }

    @Test
    fun `onPostprocessor - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : ChannelService(mockk()) {
            override val rootName: String = CHANNEL_ROOT_NAME
            override val destinations: Map<String, DestinationService> = emptyMap()

            override fun channelOnPostprocessor(
                tenantMnemonic: String,
                serviceMap: Map<String, Any>
            ): Map<String, Any> {
                throw IllegalStateException("channelOnPostprocessor exception")
            }

            override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
                TODO("Not yet implemented")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.onPostprocessor(
                VALID_DEPLOYED_NAME,
                emptyMap()
            )
        }
        assertEquals("channelOnPostprocessor exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during onPostprocessor: channelOnPostprocessor exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `sourceReader - bad tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().sourceReader(
                "unusable",
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceReader - no tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().sourceReader(
                CHANNEL_ROOT_NAME,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceReader - tenant ok in map, valid empty list result`() {
        val configurationMap = mapOf<String, Any>(
            TENANT_MNEMONIC to VALID_TENANT_ID
        )
        val actualList = TestChannelService().sourceReader(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        assertTrue(actualList.isEmpty())
    }

    @Test
    fun `sourceReader - tenant ok in channel name, valid empty list result`() {
        val configurationMap: Map<String, Any> = emptyMap()
        val actualList = TestChannelService().sourceReader(
            VALID_DEPLOYED_NAME,
            configurationMap
        )
        assertTrue(actualList.isEmpty())
    }

    @Test
    fun `sourceReader - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : ChannelService(mockk()) {
            override val rootName: String = CHANNEL_ROOT_NAME
            override val destinations: Map<String, DestinationService> = emptyMap()

            override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
                throw IllegalStateException("channelSourceReader exception")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.sourceReader(
                VALID_DEPLOYED_NAME,
                emptyMap()
            )
        }
        assertEquals("channelSourceReader exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during sourceReader: channelSourceReader exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `sourceTransformer - bad tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().sourceTransformer(
                "unusable",
                "",
                emptyServiceMap,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceTransformer - no tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().sourceTransformer(
                CHANNEL_ROOT_NAME,
                "",
                emptyServiceMap,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceTransformer - tenant ok in map, valid empty map result`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val configurationMap = mapOf<String, Any>(
            TENANT_MNEMONIC to VALID_TENANT_ID
        )
        val actualMessage = TestChannelService().sourceTransformer(
            VALID_DEPLOYED_NAME,
            "",
            emptyServiceMap,
            configurationMap
        )
        assertTrue(actualMessage.dataMap.isEmpty())
    }

    @Test
    fun `sourceTransformer - tenant ok in channel name, valid empty list result`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val configurationMap: Map<String, Any> = emptyMap()
        val actualMessage = TestChannelService().sourceTransformer(
            VALID_DEPLOYED_NAME,
            "",
            emptyServiceMap,
            configurationMap
        )
        assertTrue(actualMessage.dataMap.isEmpty())
    }

    @Test
    fun `sourceTransformer - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : ChannelService(mockk()) {
            override val rootName: String = CHANNEL_ROOT_NAME
            override val destinations: Map<String, DestinationService> = emptyMap()

            override fun channelSourceTransformer(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthMessage {
                throw IllegalStateException("channelSourceTransformer exception")
            }

            override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
                TODO("Not yet implemented")
            }
        }

        val ex = assertThrows<IllegalStateException> {
            service.sourceTransformer(
                VALID_DEPLOYED_NAME,
                "",
                emptyMap(),
                mapOf<String, Any>(TENANT_MNEMONIC to VALID_TENANT_ID)
            )
        }
        assertEquals("channelSourceTransformer exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during sourceTransformer: channelSourceTransformer exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `sourceFilter - bad tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().sourceFilter(
                "unusable",
                "",
                emptyServiceMap,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceFilter - no tenant in channel name`() {
        val emptyServiceMap = emptyMap<String, Any>()
        val ex = assertThrows<TenantMissingException> {
            TestChannelService().sourceFilter(
                CHANNEL_ROOT_NAME,
                "",
                emptyServiceMap,
                emptyServiceMap
            )
        }
        assertEquals("Could not get tenant information for the channel", ex.message)
    }

    @Test
    fun `sourceFilter - tenant ok in channel name, valid empty result`() {
        val emptyServiceMap = emptyMap<String, Any>()
        assertTrue(
            TestChannelService().sourceFilter(
                VALID_DEPLOYED_NAME,
                "",
                emptyServiceMap,
                emptyServiceMap
            ).result
        )
    }

    @Test
    fun `sourceFilter - tenant ok in map, valid empty result`() {
        val serviceMap = mapOf<String, Any>(
            TENANT_MNEMONIC to VALID_TENANT_ID,
        )
        assertTrue(
            TestChannelService().sourceFilter(
                VALID_DEPLOYED_NAME,
                "",
                serviceMap,
                serviceMap
            ).result
        )
    }

    @Test
    fun `sourceFilter - exception`() {
        val logger = mockk<KLogger>()
        val loggedException = slot<Throwable>()
        val loggedMessage = slot<() -> Any?>()
        every { logger.error(capture(loggedException), capture(loggedMessage)) } returns Unit

        mockkObject(KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns logger

        val service = object : ChannelService(mockk()) {
            override val rootName: String = CHANNEL_ROOT_NAME
            override val destinations: Map<String, DestinationService> = emptyMap()

            override fun channelSourceFilter(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthFilterResponse {
                throw IllegalStateException("channelSourceFilter exception")
            }

            override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
                TODO("Not yet implemented")
            }
        }

        val serviceMap = mapOf<String, Any>(TENANT_MNEMONIC to VALID_TENANT_ID)

        val ex = assertThrows<IllegalStateException> {
            service.sourceFilter(
                VALID_DEPLOYED_NAME,
                "",
                serviceMap,
                serviceMap
            )
        }
        assertEquals("channelSourceFilter exception", ex.message)

        assertEquals(IllegalStateException::class, loggedException.captured.javaClass.kotlin)
        assertEquals(
            "Exception encountered during sourceFilter: channelSourceFilter exception",
            loggedMessage.captured.invoke()
        )

        clearAllMocks()
    }

    @Test
    fun `destinations map - testKey - finds TestDestinationService`() {
        val response = TestChannelService().destinations["testKey"]?.destinationWriter(
            VALID_DEPLOYED_NAME,
            "",
            emptyMap(),
            emptyMap()
        )
        val expectedResponse = MirthResponse(MirthResponseStatus.SENT, "", "")
        assertEquals(expectedResponse, response)
    }
}

class TestChannelService : ChannelService(mockk()) {
    override val rootName = CHANNEL_ROOT_NAME
    override val destinations = mapOf("testKey" to TestDestinationService(rootName))
    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        return emptyList()
    }
}

class TestChannelServiceBadName : ChannelService(mockk()) {
    override val rootName = "PatientByQuestionnaireResponseLoad"
    override val destinations = mapOf("testKeyBadName" to TestDestinationService(rootName))
    override fun channelSourceReader(tenantMnemonic: String, serviceMap: Map<String, Any>): List<MirthMessage> {
        return emptyList()
    }
}
