rootProject.name = "interop-mirth"
include("mirth-channel-code")
include("mirth-channel-config")

for (project in rootProject.children) {
    project.buildFileName = "${project.name}.gradle.kts"
}

pluginManagement {
    plugins {
        id("com.projectronin.interop.gradle.integration") version "3.0.0"
        id("com.projectronin.interop.gradle.junit") version "3.0.0"
        id("com.projectronin.interop.gradle.publish") version "3.0.0"
        id("com.projectronin.interop.gradle.spring") version "3.0.0"
        id("com.projectronin.interop.gradle.version") version "3.0.0"
        id("com.github.johnrengelman.shadow") version "8.1.1"
        id("org.owasp.dependencycheck") version "8.1.2"
    }

    repositories {
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-releases/")
            mavenContent {
                releasesOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-public/")
            mavenContent {
                releasesOnly()
            }
        }
        mavenLocal()
        gradlePluginPortal()
    }
}
