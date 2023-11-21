rootProject.name = "interop-mirth"
include("mirth-channel-code")
include("mirth-channel-config")

for (project in rootProject.children) {
    project.buildFileName = "${project.name}.gradle.kts"
}

pluginManagement {
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
        maven {
            // TODO: Put into Artifactory so it's resolved to public.
            url = uri("https://repo.repsy.io/mvn/kpalang/mirthconnect")
        }
        mavenLocal()
        gradlePluginPortal()
    }
}
