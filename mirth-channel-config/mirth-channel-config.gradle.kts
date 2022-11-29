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
            defaultMnemonic = "ronin"

            auth {
                clientIdKey = "SEKI_AUTH_CLIENT_ID"
                clientSecretKey = "SEKI_AUTH_CLIENT_SECRET"
            }
        }
    }
}

dependencies {
    testImplementation(libs.interop.aidbox)
    testImplementation(libs.interop.common)
    testImplementation(libs.interop.commonJackson)
    testImplementation(libs.interop.fhir.core)
    testImplementation(libs.interop.fhir.ronin)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.javafaker)
    testImplementation(libs.bundles.jackson)
    testImplementation(libs.bundles.ktor)
    testImplementation(libs.xerces)

    implementation(project(":mirth-channel-code"))

    itImplementation(libs.mockserver.client.java)
}
