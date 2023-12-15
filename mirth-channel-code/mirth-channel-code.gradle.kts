plugins {
    alias(libs.plugins.interop.spring)
    alias(libs.plugins.interop.junit)
    alias(libs.plugins.interop.integration)
    alias(libs.plugins.interop.publish)
    // Plugin exposing shadowJar task for creating fat JAR
    alias(libs.plugins.shadow)
    alias(libs.plugins.dependencycheck)
}

dependencies {
    implementation(libs.kafka.clients)
    implementation(libs.woodstox.core)
    configurations.all {
        resolutionStrategy {
            force(libs.kafka.clients)
            force(libs.woodstox.core)
            force(libs.kotlin.stdlib)
            force(libs.spring.boot.parent)
            force(libs.jersey.bom)
        }
    }
    implementation(libs.ehr.data.authority.client)
    implementation(libs.interop.backfill.client)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.commonJackson)
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.fhir)
    implementation(libs.interop.publishers)
    implementation(libs.interop.aidbox)
    implementation(libs.interop.datalake)
    implementation(libs.interop.kafka)

    implementation(libs.interop.queue.api)
    implementation(libs.interop.queue.db)
    implementation(libs.interop.queue.kafka)
    implementation(libs.interop.rcdm.common)
    implementation(libs.interop.rcdm.transform)
    implementation(libs.bundles.interop.kafka.events)
    implementation(libs.interop.ehr.tenant)

    implementation(libs.clinical.trial.client)

    implementation(platform(libs.spring.boot.parent))
    implementation(platform(libs.jersey.bom))
    implementation(libs.spring.vault.core)
    implementation("org.springframework:spring-context")
    implementation(libs.spring.boot.autoconfigure)

    implementation(libs.jakarta.ws)
    implementation(libs.kotlin.stdlib)
    implementation(libs.mysql.connector.java)
    implementation("org.glassfish.jersey.core:jersey-client")
    implementation(libs.ronin.test.data.generator)
    implementation(libs.interop.fhirGenerators)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.bundles.jackson)
    implementation(libs.ktor.client.core)
    implementation(libs.bundles.ktor)
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.mysql)
    implementation(libs.caffeine)

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
    itImplementation(libs.rider.junit)
    itImplementation(libs.junit.pioneer)
    itImplementation(libs.mysql.connector.java)
    itImplementation(libs.ktorm.core)
    itImplementation(libs.ktorm.support.mysql)
    itImplementation("org.springframework:spring-context")
    itImplementation(libs.spring.boot.autoconfigure)
    itImplementation(libs.testcontainers.mysql)
    itImplementation(libs.junit.pioneer)
}

// Set up ShadowJar to run at the end of the Jar task, thus ensuring both are always built.
tasks.jar {
    finalizedBy(tasks.shadowJar)
}

tasks.shadowJar {
    relocate("javax.ws.rs", "interop.javax.ws.rs")
    relocate("jakarta.ws.rs", "interop.jakarta.ws.rs")
    relocate("org.glassfish.jersey", "interop.org.glassfish.jersey")
    relocate("org.glassfish.hk2", "interop.org.glassfish.hk2")
    relocate("org.jvnet.hk2", "interop.org.jvnet.hk2")
    relocate("com.fasterxml.jackson", "interop.com.fasterxml.jackson")
    relocate("io.github.classgraph", "interop.io.github.classgraph")
    // Classgraph apparently uses this as well?
    relocate("nonapi.io.github.classgraph", "interop.nonapi.io.github.classgraph")
}

tasks.withType(Test::class) {
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

dependencyCheck {
    scanConfigurations = listOf("compileClasspath", "runtimeClasspath")
    skipTestGroups = true
    suppressionFile = "${project.projectDir}/conf/owasp-suppress.xml"
}
