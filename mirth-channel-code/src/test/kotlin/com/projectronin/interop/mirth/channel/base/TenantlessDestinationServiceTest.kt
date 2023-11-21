package com.projectronin.interop.mirth.channel.base

import com.projectronin.interop.mirth.channel.enums.MirthKey
import com.projectronin.interop.mirth.channel.enums.MirthResponseStatus
import com.projectronin.interop.mirth.channel.model.MirthResponse
import com.projectronin.interop.mirth.models.MirthMessage
import com.projectronin.interop.mirth.models.destination.DestinationConfiguration
import com.projectronin.interop.mirth.models.filter.MirthFilter
import com.projectronin.interop.mirth.models.filter.MirthFilterResponse
import com.projectronin.interop.mirth.models.transformer.MirthTransformer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class TenantlessDestinationServiceTest {
    class TestDestinationService(val error: Boolean = false) : TenantlessDestinationService() {
        override fun getConfiguration(): DestinationConfiguration {
            TODO("Not yet implemented")
        }

        override fun channelDestinationWriter(
            tenantMnemonic: String,
            msg: String,
            sourceMap: Map<String, Any>,
            channelMap: Map<String, Any>
        ): MirthResponse {
            if (error) {
                throw Exception("Everything died")
            }
            return MirthResponse(MirthResponseStatus.SENT)
        }

        override fun getFilter(): MirthFilter? =
            if (!error) {
                null
            } else {
                object : MirthFilter {
                    override fun filter(
                        tenantMnemonic: String,
                        msg: String,
                        sourceMap: Map<String, Any>,
                        channelMap: Map<String, Any>
                    ): MirthFilterResponse {
                        throw Exception("Everything died")
                    }
                }
            }

        override fun getTransformer(): MirthTransformer? =
            if (!error) {
                null
            } else {
                object : MirthTransformer {
                    override fun transform(
                        tenantMnemonic: String,
                        msg: String,
                        sourceMap: Map<String, Any>,
                        channelMap: Map<String, Any>
                    ): MirthMessage {
                        throw Exception("Everything died")
                    }
                }
            }
    }

    @Test
    fun `minimal channel works`() {
        class BasicDestination : TenantlessDestinationService() {
            override fun getConfiguration(): DestinationConfiguration {
                TODO("Not yet implemented")
            }

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
        val channel = TestDestinationService(error = true)
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
        val channel = TestDestinationService(error = true)
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
