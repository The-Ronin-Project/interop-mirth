package com.projectronin.interop.mirth.util

import com.mirth.connect.connectors.js.JavaScriptDispatcherProperties
import com.mirth.connect.connectors.js.JavaScriptReceiverProperties
import com.mirth.connect.connectors.tcp.TcpDispatcherProperties
import com.mirth.connect.connectors.vm.VmDispatcherProperties
import com.mirth.connect.donkey.model.channel.DeployedState
import com.mirth.connect.donkey.model.channel.MetaDataColumn
import com.mirth.connect.donkey.model.channel.MetaDataColumnType
import com.mirth.connect.donkey.model.channel.PollingType
import com.mirth.connect.model.Channel
import com.mirth.connect.model.ChannelExportData
import com.mirth.connect.model.ChannelMetadata
import com.mirth.connect.model.ChannelPruningSettings
import com.mirth.connect.model.Connector
import com.mirth.connect.model.Filter
import com.mirth.connect.model.Rule
import com.mirth.connect.model.Step
import com.mirth.connect.model.Transformer
import com.mirth.connect.model.converters.ObjectXMLSerializer
import com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DataTypeProperties
import com.mirth.connect.plugins.datatypes.hl7v2.HL7v2SerializationProperties
import com.mirth.connect.plugins.datatypes.raw.RawDataTypeProperties
import com.mirth.connect.plugins.javascriptrule.JavaScriptRule
import com.mirth.connect.plugins.javascriptstep.JavaScriptStep
import com.mirth.connect.plugins.mllpmode.MLLPModeProperties
import com.projectronin.interop.mirth.channel.AppointmentLoad
import com.projectronin.interop.mirth.channel.MDMQueueOut
import com.projectronin.interop.mirth.channel.PatientDiscovery
import com.projectronin.interop.mirth.channel.ValidationTest
import com.projectronin.interop.mirth.channel.base.ChannelConfiguration
import com.projectronin.interop.mirth.channel.base.ChannelDestinationConfiguration
import com.projectronin.interop.mirth.channel.base.DestinationConfiguration
import com.projectronin.interop.mirth.channel.base.IntervalPollingConfig
import com.projectronin.interop.mirth.channel.base.JavaScriptDestinationConfiguration
import com.projectronin.interop.mirth.channel.base.MLLPDestinationConfiguration
import com.projectronin.interop.mirth.channel.base.MirthDestination
import com.projectronin.interop.mirth.channel.base.MirthSource
import com.projectronin.interop.mirth.channel.base.TimedPollingConfig
import io.mockk.mockkClass
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmName

class BlahTest {
    private val resourcesMap = LinkedHashMap<String, String>().apply {
        put("206087b7-266c-464f-af3b-7477264b3b89", "Interop")
    }
    private val emptyTransformer = Transformer().apply {
        elements = ArrayList()
        inboundTemplate = ""
        inboundDataType = "RAW"
        inboundProperties = RawDataTypeProperties()
        outboundTemplate = ""
        outboundDataType = "RAW"
        outboundProperties = RawDataTypeProperties()
    }
    private val emptyTransformerWithoutTemplate = Transformer().apply {
        elements = ArrayList()
        inboundDataType = "RAW"
        inboundProperties = RawDataTypeProperties()
        outboundDataType = "RAW"
        outboundProperties = RawDataTypeProperties()
    }

    @Disabled
    @Test
    fun `xml test`() {
        val channel = Channel().apply {
            id = "160b2076-2763-4772-b4ef-0ef1c78a676f" // CONFIGURABLE
            nextMetaDataId = 5 // DYNAMIC?
            name = "AppointmentLoad" // CONFIGURABLE
            description = "Version 1.0.0.\n" +
                "\n" +
                "Reads Kafka events and finds appropriate appointment based on those events" // CONFIGURABLE?
            revision = 1
            sourceConnector = Connector().apply {
                metaDataId = 0
                name = "sourceConnector"
                properties = JavaScriptReceiverProperties().apply {
                    pluginProperties = HashSet()
                    pollConnectorProperties.apply {
                        pollingType =
                            PollingType.INTERVAL // CONFIGURABLE? ValidationTest also needs some different settings
                        isPollOnStart = true
                        pollingFrequency = 5000 // CONFIGURABLE
                    }
                    sourceConnectorProperties.apply {
                        processingThreads = 1 // CONFIGURABLE
                        resourceIds = resourcesMap
                        queueBufferSize = 1000
                    }
                    script = "return sourceReader()"
                }
                transformer = emptyTransformer // CONFIGURABLE if there is a transformer OR if not RAW (MDMQueueOut)
                filter = Filter()
                transportName = "JavaScript Reader"
                mode = Connector.Mode.SOURCE
                isEnabled = true
                isWaitForPrevious = true
            }
            destinationConnectors.add(
                Connector().apply {
                    metaDataId = 1
                    name = "Publish Appointments" // CONFIGURABLE/DISCOVERABLE
                    properties = JavaScriptDispatcherProperties().apply { // CONFIGURABLE -- HL7 uses TCP
                        pluginProperties = HashSet()
                        destinationConnectorProperties.apply {
                            isQueueEnabled = true // CONFIGURABLE? Kafka queues have disabled
                            threadCount = 5 // CONFIGURABLE
                            resourceIds = resourcesMap
                            queueBufferSize = 1000
                        }
                        script = "return destinationWriter(\"publish\");" // CONFIGURABLE/DISCOVERABLE
                    }
                    transformer = emptyTransformer
                    responseTransformer = emptyTransformerWithoutTemplate
                    filter = Filter() // CONFIGURABLE -- OnboardFlag has a filter
                    transportName = "JavaScript Writer"
                    mode = Connector.Mode.DESTINATION
                    isEnabled = true
                    isWaitForPrevious = true
                }
            )
            preprocessingScript = "// Modify the message variable below to pre process data\n" +
                "return message;"
            postprocessingScript = "// This script executes once after a message has been processed\n" +
                "// Responses returned from here will be stored as \"Postprocessor\" in the response map\n" +
                "return;"
            deployScript = "// @apiinfo \"\"\"Get the channel configuration and services.\"\"\"\n" +
                "\n" +
                "var channelService = Packages.com.projectronin.interop.mirth.channel.AppointmentLoad.Companion.create();\n" + // CONFIGURABLE/DISCOVERABLE
                "onDeploy(channelService)\n"
            undeployScript = "// This script executes once when the channel is undeployed\n" +
                "// You only have access to the globalMap and globalChannelMap here to persist data\n" +
                "return;"
            properties.apply {
                initialState = DeployedState.STARTED // CONFIGURABLE? ValidationTest
                isStoreAttachments = false
                metaDataColumns.addAll(
                    listOf( // CONFIGURABLE
                        MetaDataColumn("TENANT", MetaDataColumnType.STRING, "tenantMnemonic"),
                        MetaDataColumn("RUN", MetaDataColumnType.STRING, "kafkaEventRunId"),
                        MetaDataColumn("EVENT", MetaDataColumnType.STRING, "kafkaEvent"),
                        MetaDataColumn("FAILED", MetaDataColumnType.STRING, "failureCount"),
                        MetaDataColumn("SUCCEEDED", MetaDataColumnType.STRING, "resourceCount"),
                        MetaDataColumn("SOURCE", MetaDataColumnType.STRING, "kafkaEventSource")
                    )
                )
                resourceIds = resourcesMap
            }
            exportData = ChannelExportData().apply {
                metadata = ChannelMetadata().apply {
                    pruningSettings = ChannelPruningSettings().apply {
                        pruneMetaDataDays = 14 // CONFIGURABLE -- MDM Queue Out & OnboardFlag use 60
                        isArchiveEnabled = true
                        isPruneErroredMessages = false
                    }
                }
            }
        }

        val serializer = ObjectXMLSerializer(this::class.java.classLoader)
        serializer.init("4.4.0")
        val xml = serializer.serialize(channel)
        println(xml)
        File("test-output.xml").writeText(xml)
    }

    @Disabled
    @Test
    fun `discover publishers`() {
        val appointmentLoad = constructClassWithMocks(AppointmentLoad::class)
        appointmentLoad.destinations.forEach {
            println(it.key)

            val destination = constructClassWithMocks(it.value::class)
            println(destination)
        }
    }

    @Test
    fun `generate AppointmentLoad`() {
        val channel = createChannel(AppointmentLoad.Companion)

        val serializer = ObjectXMLSerializer(this::class.java.classLoader)
        serializer.init("4.4.0")
        val xml = serializer.serialize(channel)
        File("test-AppointmentLoad.xml").writeText(xml)
    }

    @Test
    fun `generate MDMQueueOut`() {
        val channel = createChannel(MDMQueueOut.Companion)

        val serializer = ObjectXMLSerializer(this::class.java.classLoader)
        serializer.init("4.4.0")
        val xml = serializer.serialize(channel)
        File("test-MDMQueueOut.xml").writeText(xml)
    }

    @Test
    fun `generate ValidationTest`() {
        val channel = createChannel(ValidationTest.Companion)

        val serializer = ObjectXMLSerializer(this::class.java.classLoader)
        serializer.init("4.4.0")
        val xml = serializer.serialize(channel)
        File("test-ValidationTest.xml").writeText(xml)
    }

    @Test
    fun `generate PatientDiscovery`() {
        val channel = createChannel(PatientDiscovery.Companion)

        val serializer = ObjectXMLSerializer(this::class.java.classLoader)
        serializer.init("4.4.0")
        val xml = serializer.serialize(channel)
        File("test-PatientDiscovery.xml").writeText(xml)
    }

    private fun <T : MirthSource> createChannel(config: ChannelConfiguration<T>): Channel {
        val kotlinChannel = constructClassWithMocks(config.channelClass)
        val sourceTransformer = kotlinChannel.getSourceTransformer()

        val sourceTransformerElements = sourceTransformer?.let {
            ArrayList<Step>().apply {
                add(
                    JavaScriptStep().apply {
                        name = "Source Transform"
                        sequenceNumber = "0"
                        isEnabled = true
                        script = "sourceTransform()"
                    }
                )
            }
        } ?: ArrayList()
        val mirthSourceTransformer = createTransformer(config.datatype, sourceTransformerElements)

        val kotlinDestinationsByName =
            kotlinChannel.destinations.mapValues { (_, value) -> constructClassWithMocks(value::class) }

        var metadataId = 0
        return Channel().apply {
            id = config.id
            name = config.channelClass.simpleName
            description = "Version 1.0.0.\n" +
                "\n" +
                config.description
            revision = 1
            sourceConnector = Connector().apply {
                metaDataId = metadataId++
                name = "sourceConnector"
                properties = JavaScriptReceiverProperties().apply {
                    pluginProperties = HashSet()
                    pollConnectorProperties.apply {
                        when (val pollingConfig = config.pollingConfig) {
                            is IntervalPollingConfig -> {
                                pollingType = PollingType.INTERVAL
                                isPollOnStart = pollingConfig.pollOnStart
                                pollingFrequency = pollingConfig.pollingFrequency
                            }

                            is TimedPollingConfig -> {
                                pollingType = PollingType.TIME
                                isPollOnStart = pollingConfig.pollOnStart
                                pollingHour = pollingConfig.hour
                                pollingMinute = pollingConfig.minute
                            }
                        }
                    }
                    sourceConnectorProperties.apply {
                        processingThreads = config.sourceThreads
                        resourceIds = resourcesMap
                        queueBufferSize = 1000
                    }
                    script = "return sourceReader()"
                }
                transformer = mirthSourceTransformer
                filter = Filter()
                transportName = "JavaScript Reader"
                mode = Connector.Mode.SOURCE
                isEnabled = true
                isWaitForPrevious = true
            }

            kotlinChannel.getNonJavascriptDestinations().forEach { destinationConfiguration ->
                val connector = createDestination(destinationConfiguration, config.datatype, null, null, metadataId++)
                destinationConnectors.add(connector)
            }

            kotlinDestinationsByName.forEach { (key, destination) ->
                val destinationConfig = destination.getConfiguration()
                val connector = createDestination(destinationConfig, config.datatype, destination, key, metadataId++)
                destinationConnectors.add(connector)
            }
            preprocessingScript = "// Modify the message variable below to pre process data\n" +
                "return message;"
            postprocessingScript = "// This script executes once after a message has been processed\n" +
                "// Responses returned from here will be stored as \"Postprocessor\" in the response map\n" +
                "return;"
            deployScript = "// @apiinfo \"\"\"Get the channel configuration and services.\"\"\"\n" +
                "\n" +
                "var channelService = Packages.${kotlinChannel::class.jvmName}.Companion.create();\n" +
                "onDeploy(channelService)\n"
            undeployScript = "// This script executes once when the channel is undeployed\n" +
                "// You only have access to the globalMap and globalChannelMap here to persist data\n" +
                "return;"
            properties.apply {
                initialState = if (config.isStartOnDeploy) DeployedState.STARTED else DeployedState.STOPPED
                isStoreAttachments = config.storeAttachments

                config.metadataColumns.forEach { (name, mapping) ->
                    metaDataColumns.add(MetaDataColumn(name, MetaDataColumnType.STRING, mapping))
                }
                resourceIds = resourcesMap
            }
            exportData = ChannelExportData().apply {
                metadata = ChannelMetadata().apply {
                    pruningSettings = ChannelPruningSettings().apply {
                        pruneMetaDataDays = config.daysUntilPruned
                        isArchiveEnabled = true
                        isPruneErroredMessages = false
                    }
                }
            }
            nextMetaDataId = metadataId++ // DYNAMIC?
        }
    }

    private fun createTransformer(
        datatype: ChannelConfiguration.Datatype,
        steps: List<Step>
    ): Transformer {
        return Transformer().apply {
            elements = steps.ifEmpty { ArrayList() }

            when (datatype) {
                ChannelConfiguration.Datatype.HL7V2 -> {
                    inboundDataType = "HL7V2"
                    inboundProperties = HL7v2DataTypeProperties().apply {
                        (serializationProperties as HL7v2SerializationProperties).apply {
                            isStripNamespaces = true
                        }
                    }
                    outboundDataType = "HL7V2"
                    outboundProperties = HL7v2DataTypeProperties().apply {
                        (serializationProperties as HL7v2SerializationProperties).apply {
                            isStripNamespaces = true
                        }
                    }
                }

                ChannelConfiguration.Datatype.RAW -> {
                    inboundDataType = "RAW"
                    inboundProperties = RawDataTypeProperties()
                    outboundDataType = "RAW"
                    outboundProperties = RawDataTypeProperties()
                }
            }
        }
    }

    private fun createDestination(
        destinationConfig: DestinationConfiguration,
        datatype: ChannelConfiguration.Datatype,
        destination: MirthDestination?,
        key: String?,
        metadataId: Int
    ): Connector {
        return Connector(destinationConfig.name).apply {
            metaDataId = metadataId

            properties = when (destinationConfig) {
                is JavaScriptDestinationConfiguration -> {
                    transportName = "JavaScript Writer"

                    JavaScriptDispatcherProperties().apply {
                        pluginProperties = HashSet()
                        destinationConnectorProperties.apply {
                            isQueueEnabled = destinationConfig.queueEnabled
                            threadCount = destinationConfig.threadCount
                            resourceIds = resourcesMap
                            queueBufferSize = destinationConfig.queueBufferSize
                        }
                        script = "return destinationWriter(\"$key\");"
                    }
                }

                is MLLPDestinationConfiguration -> {
                    transportName = "TCP Sender"

                    TcpDispatcherProperties().apply {
                        pluginProperties = HashSet()
                        destinationConnectorProperties.apply {
                            isQueueEnabled = destinationConfig.queueEnabled
                            threadCount = destinationConfig.threadCount
                            resourceIds = resourcesMap
                            queueBufferSize = destinationConfig.queueBufferSize
                        }
                        transmissionModeProperties = MLLPModeProperties()
                        remoteAddress = destinationConfig.remoteAddress
                        remotePort = destinationConfig.remotePort
                    }
                }

                is ChannelDestinationConfiguration -> {
                    transportName = "Channel Writer"

                    VmDispatcherProperties().apply {
                        pluginProperties = HashSet()
                        destinationConnectorProperties.apply {
                            isQueueEnabled = destinationConfig.queueEnabled
                            threadCount = destinationConfig.threadCount
                            resourceIds = resourcesMap
                            queueBufferSize = destinationConfig.queueBufferSize
                        }
                        channelId = destinationConfig.channelId
                        channelTemplate = destinationConfig.channelTemplate
                        mapVariables = ArrayList<String>().apply { addAll(destinationConfig.variables) }
                    }
                }
            }

            if (destination?.getTransformer() != null) {
                throw UnsupportedOperationException("Currently do not support generating a destination with a transformer")
            }

            transformer = createTransformer(datatype, emptyList())
            responseTransformer = createTransformer(datatype, emptyList())

            val mirthFilter = destination?.getFilter()?.let {
                Filter().apply {
                    elements = ArrayList<Rule>().apply {
                        add(
                            JavaScriptRule().apply {
                                sequenceNumber = "0"
                                isEnabled = true
                                script = "return destinationFilter(\"$key\")"
                            }
                        )
                    }
                }
            } ?: Filter()

            filter = mirthFilter
            mode = Connector.Mode.DESTINATION
            isEnabled = true
            isWaitForPrevious = true
        }
    }

    private fun <T : Any> constructClassWithMocks(type: KClass<T>): T {
        val constructor = type.primaryConstructor!!
        val parameterTypes = constructor.parameters
        val parameters = parameterTypes.map {
            when (val parameterClass = it.type.classifier!! as KClass<*>) {
                String::class -> ""
                else -> mockkClass(parameterClass, relaxed = true)
            }
        }.toTypedArray()

        return constructor.call(*parameters)
    }
}
