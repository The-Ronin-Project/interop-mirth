package com.projectronin.interop.gradle.mirth.task

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.projectronin.interop.common.jackson.JacksonManager
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal

/**
 * Common configuration for tasks related to Mirth.
 */
abstract class BaseMirthTask : DefaultTask() {
    @Internal
    protected val xmlMapper = JacksonManager.setUpMapper(XmlMapper())

    init {
        group = "mirth"
    }
}
