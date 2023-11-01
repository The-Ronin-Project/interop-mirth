package com.projectronin.interop.gradle.mirth.rest.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper

internal const val MIRTH_VERSION = "4.4.0"

/**
 * Mirth transport layer for providing a List of resources.
 */
data class MirthList<T>(
    val list: T
)

/**
 * Data type defining a Date/Time.
 */
data class DateTime(
    val time: Long,
    val timezone: String
)

/**
 * Represents a Collection of "string" annotated elements within Mirth.
 */
data class StringElements(
    @JacksonXmlElementWrapper(useWrapping = false)
    val string: List<String> = listOf()
)
