package com.projectronin.interop.gradle.mirth.task

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectronin.interop.gradle.mirth.mirth
import com.projectronin.interop.gradle.mirth.rest.MirthRestClient
import com.projectronin.interop.gradle.mirth.rest.model.CodeTemplateLibrary
import com.projectronin.interop.gradle.mirth.rest.model.CodeTemplateLibraryList
import com.projectronin.interop.gradle.mirth.rest.model.MirthList
import io.ktor.http.isSuccess
import org.gradle.api.tasks.TaskAction

/**
 * Task for adding the Code Templates to Mirth.
 */
open class AddCodeTemplatesTask : BaseMirthTask() {
    @TaskAction
    fun addCodeTemplates() {
        logger.lifecycle("Adding Interop Code Template Libraries")

        val libraryDirectory = project.mirth().codeTemplateLibraryDirectory.get()

        // Create a CodeTemplateLibrary for every code template library XML file in the directory.
        val libraryFiles = libraryDirectory.asFileTree.files.filter { it.extension == "xml" }
        val codeTemplateLibraries =
            libraryFiles.map { xmlMapper.readValue<CodeTemplateLibrary>(String(it.readBytes())) }

        // We need to insert all of the Templates first.
        val codeTemplates = codeTemplateLibraries.flatMap { it.codeTemplates.codeTemplate }
        codeTemplates.forEach {
            val templateStatus = MirthRestClient.client.putCodeTemplate(it)

            if (!templateStatus.isSuccess()) {
                throw RuntimeException("Received status code $templateStatus while attempting to add Code Template ${it.id}")
            }
        }
        logger.lifecycle("Added ${codeTemplates.size} Code Templates.")

        // Then we can add the Library which has all of the references to the templates.
        val status =
            MirthRestClient.client.putCodeTemplateLibraries(MirthList(CodeTemplateLibraryList(codeTemplateLibraries)))
        if (status.isSuccess()) {
            logger.lifecycle("Added ${codeTemplateLibraries.size} Code Template Libraries")
        } else {
            throw RuntimeException("Received status code $status while attempting to add Code Template Libraries")
        }
    }
}
