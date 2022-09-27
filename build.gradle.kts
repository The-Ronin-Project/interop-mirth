plugins {
    id("com.projectronin.interop.gradle.version")
    id("com.projectronin.interop.gradle.spring")
    id("com.projectronin.interop.gradle.publish") apply false
    id("com.projectronin.interop.gradle.junit")

    // Plugin exposing shadowJar task for creating fat JAR
    id("com.github.johnrengelman.shadow")
}

subprojects {
    apply(plugin = "com.projectronin.interop.gradle.publish")

    // Disable releases hub from running on the subprojects. Main project will handle it all.
    tasks.filter { it.group.equals("releases hub", ignoreCase = true) }.forEach { it.enabled = false }
}
