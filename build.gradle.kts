plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

allprojects {
    group = project.findProperty("PROJECT_GROUP")?.toString() ?: "io.github.santimattius"
    version = project.findProperty("PROJECT_VERSION")?.toString() ?: "0.1.0"

    repositories {
        mavenCentral()
        google()
    }

    // Skip signing when no signatory is configured (e.g. publishToMavenLocal without GPG)
    afterEvaluate {
        tasks.matching { it.name.startsWith("sign") }.configureEach {
            enabled = project.findProperty("signingInMemoryKey") != null
        }
    }
}

// Run tests for all modules that have unit tests (excludes :sample, which fails compilation by design)
tasks.register("testAll") {
    group = "verification"
    description = "Runs tests in compiler, detekt-rules, gradle-plugin, lint-rules, intellij-plugin (excludes sample)"
    dependsOn(
        ":compiler:test",
        ":detekt-rules:test",
        ":gradle-plugin:test",
        ":lint-rules:test",
        ":intellij-plugin:test",
    )
}

// Validate that the sample project fails compilation with expected compiler rule codes (run as part of :compiler:test)
tasks.register("validateSample") {
    group = "verification"
    description = "Validates :sample with the compiler: runs compiler tests which include 'sample project fails compilation with expected rule codes'"
    dependsOn(":compiler:test")
}

// Aggregate task to publish all library modules to Maven Central
tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Publishes all library modules (annotations, compiler, detekt-rules, gradle-plugin, lint-rules) to Maven Central"
    dependsOn(
        ":annotations:publishToMavenCentral",
        ":compiler:publishToMavenCentral",
        ":detekt-rules:publishToMavenCentral",
        ":gradle-plugin:publishToMavenCentral",
    )
}
