plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.interop.base)
}

dependencies {
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.aidbox)
    implementation(libs.interop.publishers)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.fhir)

    implementation(libs.bundles.jackson)
    implementation(libs.bundles.ktor)
}

gradlePlugin {
    plugins {
        create("mirthPlugin") {
            id = "com.projectronin.interop.gradle.mirth"
            implementationClass = "com.projectronin.interop.gradle.mirth.MirthPlugin"
        }
    }
}
