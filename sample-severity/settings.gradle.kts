pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    val kotlinVersion: String by settings
    val structuredCoroutinesVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        id("io.github.santimattius.structured-coroutines") version structuredCoroutinesVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "sample-severity"
