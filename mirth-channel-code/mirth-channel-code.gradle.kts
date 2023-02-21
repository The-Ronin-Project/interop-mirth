plugins {
    id("com.projectronin.interop.gradle.version")
    id("com.projectronin.interop.gradle.publish")
    id("com.projectronin.interop.gradle.junit")
    id("com.projectronin.interop.gradle.integration")
    id("com.projectronin.interop.gradle.spring")
    // Plugin exposing shadowJar task for creating fat JAR
    id("com.github.johnrengelman.shadow")
    id("org.owasp.dependencycheck")
}

dependencies {
    // Force versions
    implementation(libs.kafka.clients) {
        isForce = true
    }
    implementation(libs.woodstox.core) {
        isForce = true
    }

    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.fhir)
    implementation(libs.interop.publishers.core)
    implementation(libs.interop.publishers.aidbox)
    implementation(libs.interop.publishers.datalake)
    implementation(libs.interop.publishers.kafka)
    implementation(libs.interop.queue.api)
    implementation(libs.interop.queue.db)
    implementation(libs.interop.queue.kafka)
    implementation(libs.interop.ehr.fhir.ronin) {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    implementation(libs.bundles.interop.kafka.events)

    implementation(libs.interop.ehr.tenant)
    implementation(libs.spring.vault.core)
    implementation("org.springframework:spring-context")
    implementation(libs.spring.boot.autoconfigure)

    implementation(libs.jakarta.ws)
    implementation(libs.kotlin.stdlib)
    implementation(libs.mysql.connector.java)
    implementation(libs.jersey.glassfish.client)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.ktor.client.core)
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.mysql)

    runtimeOnly(libs.interop.ehr.epic) {
        exclude(group = "org.springframework.boot")
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    runtimeOnly(libs.interop.ehr.cerner) {
        exclude(group = "org.springframework.boot")
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    runtimeOnly(libs.interop.validation.client) {
        exclude(group = "org.springframework.boot")
        exclude(group = "org.yaml", module = "snakeyaml")
    }

    testImplementation(libs.mockk)
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.interop.ehr.liquibase)
    testImplementation(libs.interop.queue.liquibase)
    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.rider.junit)

    // Allows us to change environment variables
    testImplementation(libs.junit.pioneer)

    testRuntimeOnly(libs.bundles.test.mysql)

    itImplementation(libs.guava)
}

// Set up ShadowJar to run at the end of the Jar task, thus ensuring both are always built.
tasks.jar {
    finalizedBy(tasks.shadowJar)
}

tasks.shadowJar {
    relocate("javax.ws.rs", "interop.javax.ws.rs")
    relocate("org.glassfish.jersey", "interop.org.glassfish.jersey")
    relocate("org.glassfish.hk2", "interop.org.glassfish.hk2")
    relocate("org.jvnet.hk2", "interop.org.jvnet.hk2")
}

tasks.withType(Test::class) {
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
}

dependencyCheck {
    scanConfigurations = listOf("compileClasspath", "runtimeClasspath")
    skipTestGroups = true
    suppressionFile = "${project.projectDir}/conf/owasp-suppress.xml"
}
