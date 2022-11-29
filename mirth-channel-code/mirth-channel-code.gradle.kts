plugins {
    id("com.projectronin.interop.gradle.version")
    id("com.projectronin.interop.gradle.publish")
    id("com.projectronin.interop.gradle.junit")
    // Plugin exposing shadowJar task for creating fat JAR
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(libs.interop.aidbox)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.ehr.epic)
    implementation(libs.interop.fhir.core)
    implementation(libs.interop.publishers)
    implementation(libs.interop.datalake)
    implementation(libs.interop.queue.api)
    implementation(libs.interop.queue.db)
    implementation(libs.interop.tenant)
    implementation(libs.interop.fhir.ronin)

    implementation(libs.jakarta.ws)
    implementation(libs.kotlin.stdlib)
    implementation(libs.mysql.connector.java)
    implementation(libs.jersey.glassfish.client)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.ktor.client.core)
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.mysql)

    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.interop.ehr.liquibase)
    testImplementation(libs.interop.queue.liquibase)
    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.rider.junit)

    // Allows us to change environment variables
    testImplementation(libs.junit.pioneer)

    testRuntimeOnly(libs.bundles.test.mysql)
}

// Set up ShadowJar to run at the end of the Jar task, thus ensuring both are always built.
tasks.jar {
    finalizedBy(tasks.shadowJar)
}

tasks.shadowJar {
    relocate("jakarta.ws.rs", "interop.jakarta.ws.rs")
    relocate("org.glassfish.jersey", "interop.org.glassfish.jersey")
    relocate("org.glassfish.hk2", "interop.org.glassfish.hk2")
    relocate("org.jvnet.hk2", "interop.org.jvnet.hk2")
}

tasks.withType(Test::class) {
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
}
