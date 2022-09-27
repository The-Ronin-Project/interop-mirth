package com.projectronin.interop.gradle.mirth.rest.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * Model for a Mirth Directory resource.
 */
@JsonPropertyOrder(
    "@version",
    "pluginPointName",
    "type",
    "id",
    "name",
    "description",
    "includeWithGlobalScripts",
    "directory",
    "directoryRecursion"
)
data class DirectoryResource(
    val id: String,
    val name: String,
    val description: String,
    val includeWithGlobalScripts: Boolean,
    val directory: String,
    val directoryRecursion: Boolean = true
) {
    @JsonProperty("@version")
    val version: String = MIRTH_VERSION
    val pluginPointName: String = "Directory Resource"
    val type: String = "Directory"
}

/**
 * Model for a List of [DirectoryResource]s.
 */
data class DirectoryResourceList(
    @JsonProperty("com.mirth.connect.plugins.directoryresource.DirectoryResourceProperties") val directoryResources: List<DirectoryResource>
)

const val INTEROP_RESOURCE_ID = "206087b7-266c-464f-af3b-7477264b3b89"
const val INTEROP_DIRECTORY = "interop"
