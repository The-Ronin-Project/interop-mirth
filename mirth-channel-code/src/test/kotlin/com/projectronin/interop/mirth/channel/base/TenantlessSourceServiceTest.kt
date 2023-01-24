package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.exceptions.MapVariableMissing
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

private const val CHANNEL_ROOT_NAME = "TenantlessChannel"

class TenantlessSourceServiceTest {
    @Test
    fun `onDeploy - name errors`() {
        val channel = TestChannelService()
        assertDoesNotThrow {
            channel.onDeploy("ronin-KafkaPatientQueue", emptyMap())
        }

        assertThrows<Exception> {
            channel.onDeploy("thisnameiscompletelyandutterlymcuhtoolongohno", emptyMap())
        }

        assertThrows<Exception> {
            BadTestChannelService().onDeploy("thisnameiscompletelyandutterlymcuhtoolongohno", emptyMap())
        }
    }

    @Test
    fun `minimal channel works`() {
        class BasicSource : TenantlessSourceService() {
            override val rootName = CHANNEL_ROOT_NAME
            override val destinations = emptyMap<String, TenantlessDestinationService>()
            override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
                return emptyList()
            }
        }
        val channel = BasicSource()
        assertDoesNotThrow { channel.onDeploy("blah", emptyMap()) }
        assertDoesNotThrow { channel.sourceReader("blah", emptyMap()) }
        assertDoesNotThrow {
            channel.sourceFilter(
                "blah",
                "test",
                mapOf(MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
        assertDoesNotThrow {
            channel.sourceTransformer(
                "blah",
                "test",
                mapOf(MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
    }

    @Test
    fun `all calls - channel can error`() {
        val channel = TestChannelService()
        assertThrows<Exception> {
            channel.onDeploy("blah", mapOf("Error" to true))
        }
        assertThrows<Exception> {
            channel.sourceReader("blah", mapOf("Error" to true))
        }
        assertThrows<Exception> {
            channel.sourceFilter(
                "blah",
                "error",
                mapOf("Error" to true, MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
        assertThrows<Exception> {
            channel.sourceTransformer(
                "blah",
                "error",
                mapOf("Error" to true, MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
    }

    @Test
    fun `tenant required`() {
        val channel = TestChannelService()
        assertThrows<Exception> {
            channel.sourceTransformer(
                "blah",
                "error",
                emptyMap(),
                emptyMap()
            )
        }

        assertThrows<Exception> {
            channel.sourceFilter(
                "blah",
                "error",
                emptyMap(),
                emptyMap()
            )
        }

        assertThrows< MapVariableMissing> { channel.sourceReader("test", mapOf("Bad Message" to true)) }
    }
    class TestChannelService : TenantlessSourceService() {
        override val rootName = CHANNEL_ROOT_NAME
        override val destinations = emptyMap<String, TenantlessDestinationService>()
        override fun channelOnDeploy(serviceMap: Map<String, Any>): Map<String, Any> {
            if (serviceMap.containsKey("Error")) {
                throw Exception("Everything died")
            }

            return super.channelOnDeploy(serviceMap)
        }
        override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
            if (serviceMap.containsKey("Error")) {
                throw Exception("Everything died")
            }
            if (serviceMap.containsKey("Bad Message")) {
                return listOf(MirthMessage("oops forgot to tag tenant"))
            }
            return listOf(MirthMessage("Good Mesasage", mapOf(MirthKey.TENANT_MNEMONIC.code to "yes")))
        }

        override fun channelSourceFilter(
            tenantMnemonic: String,
            msg: String,
            sourceMap: Map<String, Any>,
            channelMap: Map<String, Any>
        ): MirthFilterResponse {
            if (sourceMap.containsKey("Error")) {
                throw Exception("Everything died")
            }
            return super.channelSourceFilter(tenantMnemonic, msg, sourceMap, channelMap)
        }

        override fun channelSourceTransformer(
            tenantMnemonic: String,
            msg: String,
            sourceMap: Map<String, Any>,
            channelMap: Map<String, Any>
        ): MirthMessage {
            if (sourceMap.containsKey("Error")) {
                throw Exception("Everything died")
            }
            return super.channelSourceTransformer(tenantMnemonic, msg, sourceMap, channelMap)
        }
    }

    class BadTestChannelService : TenantlessSourceService() {
        override val rootName = "thisnameiscompletelyandutterlymcuhtoolongohno"
        override val destinations = emptyMap<String, TenantlessDestinationService>()
        override fun channelSourceReader(serviceMap: Map<String, Any>): List<MirthMessage> {
            return emptyList()
        }
    }
}
