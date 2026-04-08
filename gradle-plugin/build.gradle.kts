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

// Generate a properties file with the project version so the plugin can resolve it at runtime
val generateVersionProperties = tasks.register("generateVersionProperties") {
    val propsFile = layout.buildDirectory.file("resources/main/structured-coroutines.properties")
    outputs.file(propsFile)
    inputs.property("version", project.version)
    doFirst {
        propsFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=${project.version}\n")
        }
    }
}
tasks.named("processResources") {
    dependsOn(generateVersionProperties)
}

// Custom task that runs all checks for the Gradle plugin
tasks.register("structuredCoroutinesCheck") {
    group = "verification"
    description = "Runs all checks for the Gradle plugin"
    dependsOn(tasks.validatePlugins, tasks.test)
}