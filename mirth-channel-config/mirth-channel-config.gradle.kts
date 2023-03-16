plugins {
    id("com.projectronin.interop.gradle.base")
    id("com.projectronin.interop.gradle.mirth")
    id("com.projectronin.interop.gradle.integration")
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
    itImplementation(libs.interop.publishers.aidbox)
    itImplementation(libs.interop.common)
    itImplementation(libs.interop.commonJackson)

    itImplementation(libs.interop.fhir)
    itImplementation(libs.interop.ehr.fhir.ronin)

    itImplementation(libs.bundles.jackson)
    itImplementation(libs.bundles.ktor)
    itImplementation(libs.xerces)

    itImplementation(project(":mirth-channel-code"))
    itImplementation(libs.mockserver.client.java)
    itImplementation(libs.interop.fhir.generators)
    itImplementation(libs.interop.queue.kafka)
    itImplementation(libs.interop.publishers.kafka)
    itImplementation(libs.bundles.interop.kafka.events)
    itImplementation(libs.ronin.test.data.generator)
    itImplementation(libs.ronin.kafka)
    itImplementation("io.github.microutils:kotlin-logging:3.0.5")
}
