package com.projectronin.interop.gradle.postman.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "testsuites")
data class TestRun(
    @field:JacksonXmlProperty(isAttribute = true)
    val name: String,
    @field:JacksonXmlProperty(isAttribute = true)
    val tests: Int,
    @field:JacksonXmlProperty(isAttribute = true)
    val time: String,
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val testsuite: List<TestSuite> = emptyList()
)

data class TestSuite(
    @field:JacksonXmlProperty(isAttribute = true)
    val name: String,
    @field:JacksonXmlProperty(isAttribute = true)
    val timestamp: String,
    @field:JacksonXmlProperty(isAttribute = true)
    val tests: Int,
    @field:JacksonXmlProperty(isAttribute = true)
    val failures: Int,
    @field:JacksonXmlProperty(isAttribute = true)
    val errors: Int,
    @field:JacksonXmlProperty(isAttribute = true)
    val time: String,
    @field:JacksonXmlProperty(localName = "system-err")
    val systemErr: String?,
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val testcase: List<TestCase> = emptyList()
)

data class TestCase(
    @field:JacksonXmlProperty(isAttribute = true)
    val name: String,
    @field:JacksonXmlProperty(isAttribute = true)
    val time: String,
    @field:JacksonXmlProperty(isAttribute = true)
    val classname: String,
    val failure: Failure?,
    val error: Error?,
    val skipped: Skipped?
)

data class Failure(
    @field:JacksonXmlProperty(isAttribute = true)
    val type: String?,
    @field:JacksonXmlProperty(isAttribute = true)
    val message: String?
)

data class Error(
    @field:JacksonXmlProperty(isAttribute = true)
    val type: String?,
    @field:JacksonXmlProperty(isAttribute = true)
    val message: String?
)

data class Skipped(
    @field:JacksonXmlProperty(isAttribute = true)
    val message: String?
)
