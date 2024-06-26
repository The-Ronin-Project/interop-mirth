plugins {
    alias(libs.plugins.interop.integration)
    id("com.projectronin.interop.gradle.mirth")
}

sonar {
    isSkipProject = true
}

mirth {
    dockerDirectory = layout.buildDirectory.dir("../dev-env")
    mirthConnectorLibrary = project(":mirth-channel-code")
    codeTemplateLibraryDirectory = layout.buildDirectory.dir("../code-template-libraries")

    channel {
        baseDirectory = layout.buildDirectory.dir("../channels")

        tenant {
            defaultMnemonic = "epicmock"

            auth {
                clientIdKey = "SEKI_AUTH_CLIENT_ID"
                clientSecretKey = "SEKI_AUTH_CLIENT_SECRET"
            }
        }
    }
}

dependencies {
    itImplementation(libs.interop.backfill.client)

    itImplementation(libs.interop.common)
    itImplementation(libs.interop.commonHttp)
    itImplementation(libs.interop.commonJackson)

    itImplementation(libs.interop.fhir)
    itImplementation(libs.interop.rcdm.transform)
    itImplementation(libs.interop.ehr.fhir.ronin.generators)

    itImplementation(libs.bundles.jackson)
    itImplementation(libs.bundles.ktor)
    itImplementation(libs.xerces)

    itImplementation(project(":mirth-channel-code"))
    itImplementation(libs.mockserver.client.java)
    itImplementation(libs.interop.fhirGenerators)
    itImplementation(libs.interop.queue.kafka)
    itImplementation(libs.bundles.interop.kafka.events)
    itImplementation(libs.ronin.test.data.generator)
    itImplementation(libs.ronin.kafka)
    itImplementation(libs.interop.kafka)
    itImplementation(libs.interop.kafka.testing.client)
    itImplementation(libs.interop.aidbox)
    itImplementation(libs.kotlin.logging)
}

tasks.withType<Test> {
    maxHeapSize = "2g"
}
