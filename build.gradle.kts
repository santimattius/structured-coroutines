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
        //":lint-rules:publishMavenPublicationToMavenCentralRepository", TODO: review
    )
}
