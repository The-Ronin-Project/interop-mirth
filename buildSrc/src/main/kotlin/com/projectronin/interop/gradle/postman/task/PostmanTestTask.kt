package com.projectronin.interop.gradle.postman.task

import com.projectronin.interop.gradle.mirth.ChannelExtension
import com.projectronin.interop.gradle.mirth.mirth
import com.projectronin.interop.gradle.mirth.task.BaseMirthTask
import com.projectronin.interop.gradle.postman.model.TestRun
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

/**
 * Task for running and reporting on Postman tasks.
 */
open class PostmanTestTask @Inject constructor(private val execOperations: ExecOperations) : BaseMirthTask() {
    private val testFolder = "test"
    private val postmanCollectionSuffix = ".postman_collection.json"
    private val postmanEnvironmentSuffix = ".postman_environment.json"

    @TaskAction
    fun executeTests() {
        logger.lifecycle("Running postman tests")

        val mirthExtension = project.mirth()
        val channels = getChannelsWithTests(mirthExtension.channel)

        val testReportsDirectory = mirthExtension.testReportsDirectory.get()
        clearReportsDirectory(testReportsDirectory)
        processTestsForChannels(channels, testReportsDirectory)
        reportOnTests(testReportsDirectory)
    }

    /**
     * Retrieves all channels with a test directory. This will return the File marking the actual channel folder, not the test folder.
     */
    private fun getChannelsWithTests(channelExtension: com.projectronin.interop.gradle.mirth.ChannelExtension): List<File> {
        val channelsDirectory = channelExtension.baseDirectory.get()
        return channelsDirectory.asFile.listFiles()?.filter {
            if (it.isDirectory) {
                it.listFiles()?.any { c -> c.isDirectory && c.name == testFolder } ?: false
            } else {
                false
            }
        } ?: emptyList()
    }

    /**
     * Truncates all data from the provided directory.
     */
    private fun clearReportsDirectory(testReportsDirectory: Directory) {
        testReportsDirectory.asFile.listFiles()?.forEach { it.delete() }
    }

    /**
     * Processes all tests for the supplied [channels], placing their test reports in the [testReportsDirectory].
     */
    private fun processTestsForChannels(channels: List<File>, testReportsDirectory: Directory) {
        val outputPath = testReportsDirectory.asFile.absolutePath
        logger.lifecycle("Output path for Postman tests is: $outputPath")

        channels.forEach { channel ->
            val testFolder = channel.listFiles { _, name -> name == testFolder }?.get(0)
                ?: throw IllegalStateException("No test folder found for ${channel.name}")

            val collections = getPostmanCollections(testFolder)
            val environment = getPostmanEnvironment(testFolder)
            val environmentString = if (environment == null) "" else "-e /input/${environment.name}"

            val testPath = testFolder.absolutePath
            val simpleDate = SimpleDateFormat("yyyy/MM/dd hh:mm:ss.SSS")

            collections.forEach { collection ->
                val startDate = simpleDate.format(Date())
                logger.lifecycle("$startDate - Launched test collection ${collection.name} ${environment?.let { "with environment ${it.name}" }}")
                execOperations.exec {
                    workingDir(testPath)
                    isIgnoreExitValue = true

                    // Command explained:
                    // --rm ensures that the container will be removed after running.
                    // -v is setting our input and output volumes so we can get the results from the docker image
                    // --network=host is ensuring access to "localhost" is properly configured
                    // -t allows us to directly run commands on the docker node
                    // postman/newman is the actual image we are running.
                    // The rest is the command to newman telling it to run our collection using an optional environment,
                    // and to output the results using JUnit format, exporting the files to our output directory.
                    // -x tells postman/newman to continue running each collection all the way to the end, regardless
                    // of individual case failures. -x reports all errors encountered and runs the ending cleanup calls.
                    commandLine(
                        "docker run --rm -v $testPath:/input -v $outputPath:/output --network=host -t postman/newman run /input/${collection.name} $environmentString -x -r junit --reporter-junit-export /output".split(
                            " "
                        )
                    )
                }
                val endDate = simpleDate.format(Date())
                logger.lifecycle("$endDate - Completed test collection ${collection.name} ${environment?.let { "with environment ${it.name}" }}")
            }
        }
    }

    /**
     * Retrieves the Postman environment file for the [channelTestFolder], if one exists.
     */
    private fun getPostmanEnvironment(channelTestFolder: File): File? {
        val environmentFiles =
            channelTestFolder.listFiles { _, name -> name.endsWith(postmanEnvironmentSuffix) }?.toList()
                ?: emptyList<File>()
        return when (environmentFiles.size) {
            0 -> null
            1 -> environmentFiles[0]
            else -> throw IllegalStateException("Multiple environment files found in ${channelTestFolder.path}")
        }
    }

    /**
     * Retrieves all Postman collections from the [channelTestFolder].
     */
    private fun getPostmanCollections(channelTestFolder: File): List<File> {
        return channelTestFolder.listFiles { _, name -> name.endsWith(postmanCollectionSuffix) }?.toList()
            ?: emptyList()
    }

    /**
     * Outputs the results of processing tests based on the data in the [testReportsDirectory]. Any failures or errors will result in failing the build.
     */
    private fun reportOnTests(testReportsDirectory: Directory) {
        val testRuns = getTestRuns(testReportsDirectory)

        val totalTests = testRuns.sumOf { it.tests }
        logger.lifecycle("$totalTests test cases ran.")

        val failures = testRuns.sumOf { runs -> runs.testsuite.sumOf { suite -> suite.failures } }
        val errors = testRuns.sumOf { runs -> runs.testsuite.sumOf { suite -> suite.errors } }
        if (failures == 0 && errors == 0) {
            logger.lifecycle("All tests passed")
            return
        }

        if (failures > 0) {
            logger.lifecycle("")
            logger.lifecycle("$failures failures:")

            testRuns.forEach { runs ->
                runs.testsuite.forEach { suite ->
                    if (suite.failures > 0) {
                        suite.testcase.forEach { case ->
                            case.failure?.let {
                                logger.lifecycle("${runs.name} - ${suite.name} - ${case.name}: ${it.message}")
                            }
                        }
                    }
                }
            }
            logger.lifecycle("")
        }

        if (errors > 0) {
            logger.lifecycle("$errors errors:")

            testRuns.forEach { runs ->
                runs.testsuite.forEach { suite ->
                    if (suite.errors > 0) {
                        suite.testcase.forEach { case ->
                            case.error?.let {
                                logger.lifecycle("${runs.name} - ${suite.name} - ${case.name}: ${it.message}")
                            }
                        }
                        suite.systemErr?.let {
                            logger.lifecycle("${runs.name} - ${suite.name} - Error output: $it")
                        }
                    }
                }
            }
            logger.lifecycle("")
        }

        throw GradleException("Tests did not complete successfully")
    }

    /**
     * Retrieves all [TestRun]s from the [testReportsDirectory].
     */
    private fun getTestRuns(testReportsDirectory: Directory): List<TestRun> {
        return testReportsDirectory.asFile.listFiles()?.map {
            xmlMapper.readValue(it, TestRun::class.java)
        } ?: emptyList()
    }
}
