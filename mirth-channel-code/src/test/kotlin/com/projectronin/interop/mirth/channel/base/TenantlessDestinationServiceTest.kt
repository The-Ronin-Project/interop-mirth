package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthFilterResponse
import com.projectronin.interop.mirth.channel.model.MirthMessage
import com.projectronin.interop.mirth.channel.model.MirthResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class TenantlessDestinationServiceTest {
    class TestDestinationService() : TenantlessDestinationService() {
        override fun channelDestinationWriter(
            tenantMnemonic: String,
            msg: String,
            sourceMap: Map<String, Any>,
            channelMap: Map<String, Any>
        ): MirthResponse {
            if (sourceMap.containsKey("Error")) {
                throw Exception("Everything died")
            }
            return MirthResponse(MirthResponseStatus.SENT)
        }

        override fun channelDestinationFilter(
            tenantMnemonic: String,
            msg: String,
            sourceMap: Map<String, Any>,
            channelMap: Map<String, Any>
        ): MirthFilterResponse {
            if (sourceMap.containsKey("Error")) {
                throw Exception("Everything died")
            }
            return super.channelDestinationFilter(tenantMnemonic, msg, sourceMap, channelMap)
        }

        override fun channelDestinationTransformer(
            unusedValue: String,
            msg: String,
            sourceMap: Map<String, Any>,
            channelMap: Map<String, Any>
        ): MirthMessage {
            if (sourceMap.containsKey("Error")) {
                throw Exception("Everything died")
            }
            return super.destinationTransformer(unusedValue, msg, sourceMap, channelMap)
        }
    }

    @Test
    fun `minimal channel works`() {
        class BasicDestination : TenantlessDestinationService() {
            override fun channelDestinationWriter(
                tenantMnemonic: String,
                msg: String,
                sourceMap: Map<String, Any>,
                channelMap: Map<String, Any>
            ): MirthResponse {
                return MirthResponse(MirthResponseStatus.SENT)
            }
        }
        val channel = BasicDestination()
        assertDoesNotThrow {
            channel.destinationFilter(
                "blah",
                "test",
                mapOf(MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
        assertDoesNotThrow {
            channel.destinationTransformer(
                "blah",
                "test",
                mapOf(MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
        assertDoesNotThrow {
            channel.destinationWriter(
                "blah",
                "test",
                mapOf(MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
    }

    @Test
    fun `all calls - destination can error`() {
        val channel = TestDestinationService()
        assertThrows<Exception> {
            channel.destinationFilter(
                "blah",
                "error",
                mapOf("Error" to true, MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
        assertThrows<Exception> {
            channel.destinationTransformer(
                "blah",
                "error",
                mapOf("Error" to true, MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
        assertThrows<Exception> {
            channel.destinationWriter(
                "blah",
                "error",
                mapOf("Error" to true, MirthKey.TENANT_MNEMONIC.code to "present"),
                emptyMap()
            )
        }
    }

    @Test
    fun `tenant required`() {
        val channel = TestDestinationService()
        assertThrows<Exception> {
            channel.destinationFilter(
                "blah",
                "error",
                emptyMap(),
                emptyMap()
            )
        }

        assertThrows<Exception> {
            channel.destinationTransformer(
                "blah",
                "error",
                emptyMap(),
                emptyMap()
            )
        }

        assertThrows<Exception> {
            channel.destinationWriter(
                "blah",
                "error",
                emptyMap(),
                emptyMap()
            )
        }
    }
}
