plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.projectronin.interop.gradle.base")
}

dependencies {
    implementation("com.projectronin.interop:interop-common-jackson:3.0.0")
    implementation("com.projectronin.interop.publish:interop-aidbox:4.0.0")
    implementation("com.projectronin.interop.publish:interop-publishers:4.0.0")
    implementation("com.projectronin.interop:interop-common-http:3.0.0")
    implementation("com.projectronin.interop.fhir:interop-fhir:3.0.0")

    implementation("com.fasterxml.jackson.core:jackson-core:2.13.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.2")

    implementation("io.ktor:ktor-client-auth:2.0.2")
    implementation("io.ktor:ktor-client-cio:2.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:2.0.2")
    implementation("io.ktor:ktor-client-core:2.0.2")
    implementation("io.ktor:ktor-client-logging:2.0.2")
    implementation("io.ktor:ktor-serialization-jackson:2.0.2")
}

gradlePlugin {
    plugins {
        create("mirthPlugin") {
            id = "com.projectronin.interop.gradle.mirth"
            implementationClass = "com.projectronin.interop.gradle.mirth.MirthPlugin"
        }
    }
}
