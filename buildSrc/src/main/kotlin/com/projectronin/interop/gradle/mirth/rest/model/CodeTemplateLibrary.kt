package com.projectronin.interop.gradle.mirth.rest.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper

/**
 * Wrapper List for [CodeTemplateLibraries][CodeTemplateLibrary]
 */
data class CodeTemplateLibraryList(
    val codeTemplateLibrary: List<CodeTemplateLibrary>
)

/**
 * Model for a Code Template Library in Mirth.
 */
@JsonPropertyOrder(
    "@version",
    "id",
    "name",
    "revision",
    "lastModified",
    "description",
    "includeNewChannels",
    "enabledChannelIds",
    "disabledChannelIds",
    "codeTemplates"
)
data class CodeTemplateLibrary(
    val id: String,
    val name: String,
    val revision: Int,
    val lastModified: DateTime,
    val description: String?,
    val includeNewChannels: Boolean,
    val enabledChannelIds: StringElements?,
    val disabledChannelIds: StringElements?,
    val codeTemplates: CodeTemplateList
) {
    @JsonProperty("@version")
    val version: String = MIRTH_VERSION
}

/**
 * Wrapper List for [CodeTemplate]s
 */
data class CodeTemplateList(
    @JacksonXmlElementWrapper(useWrapping = false)
    val codeTemplate: List<CodeTemplate>
)

/**
 * Model for a Code Template in Mirth.
 */
@JsonPropertyOrder(
    "@version",
    "id",
    "name",
    "revision",
    "lastModified",
    "contextSet",
    "properties"
)
data class CodeTemplate(
    val id: String,
    val name: String,
    val revision: Int,
    val lastModified: DateTime,
    val contextSet: ContextSet,
    val properties: CodeTemplateProperties
) {
    @JsonProperty("@version")
    val version: String = MIRTH_VERSION
}

/**
 * Model for Code Template Properties in Mirth.
 */
@JsonPropertyOrder(
    "@class",
    "type",
    "code"
)
data class CodeTemplateProperties(
    val type: String,
    val code: String
) {
    @JsonProperty("@class")
    val clazz: String = "com.mirth.connect.model.codetemplates.BasicCodeTemplateProperties"
}

/**
 * Model for a Context Set in Mirth.
 */
data class ContextSet(
    val delegate: ContextSetDelegate
)

/**
 * Wrapper List for Mirth Context Types
 */
data class ContextSetDelegate(
    @JacksonXmlElementWrapper(useWrapping = false)
    val contextType: List<String> = listOf()
)
