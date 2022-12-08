package com.projectronin.interop.mirth.channels.client.mirth

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.projectronin.interop.common.jackson.getAs
import com.projectronin.interop.common.jackson.getAsList

data class MessageWrapper(val message: Message)

data class Message(val messageId: Int, val connectorMessages: ConnectorMessages) {
    private val parsedConnectorMessages: List<ConnectorMessage> by lazy {
        connectorMessages.entry.map { it.connectorMessage }
    }
    val sourceStatus: String by lazy { parsedConnectorMessages.first { it.connectorName == "Source" }.status }
    val destinationMessages: List<ConnectorMessage> by lazy {
        parsedConnectorMessages.filter { it.connectorName != "Source" }
    }
}

@JsonDeserialize(using = ConnectorMessagesDeserializer::class)
data class ConnectorMessages(val entry: List<ConnectorMessageWrapper>)

data class ConnectorMessageWrapper(val connectorMessage: ConnectorMessage)

data class ConnectorMessage(val connectorName: String, val status: String)

class ConnectorMessagesDeserializer : StdDeserializer<ConnectorMessages>(ConnectorMessages::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ConnectorMessages {
        val node = p.codec.readTree<JsonNode>(p) ?: throw JsonParseException(p, "Unable to parse node")
        val entry = node.get("entry")
        return if (!entry.isArray) {
            ConnectorMessages(entry = listOf(node.getAs("entry", p)))
        } else {
            ConnectorMessages(entry = node.getAsList("entry", p))
        }
    }
}
