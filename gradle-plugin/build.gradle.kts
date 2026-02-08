plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.java.gradle.plugin)
    alias(libs.plugins.maven.publish.vanniktech)
}


dependencies {
    // Only Gradle API and Kotlin Gradle Plugin API
    // NO compiler dependencies here
    implementation(gradleApi())
    implementation(libs.kotlin.gradle.plugin.api)
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("structuredCoroutines") {
            id = "io.github.santimattius.structured-coroutines"
            implementationClass = "io.github.santimattius.structured.gradle.StructuredCoroutinesGradlePlugin"
            displayName = "Structured Coroutines"
            description = "Kotlin Compiler Plugin that enforces structured concurrency rules"
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-gradle-plugin",
        version = project.version.toString()
    )
}

// Custom task that runs all checks for the Gradle plugin
tasks.register("structuredCoroutinesCheck") {
    group = "verification"
    description = "Runs all checks for the Gradle plugin"
    dependsOn(tasks.validatePlugins, tasks.test)
}