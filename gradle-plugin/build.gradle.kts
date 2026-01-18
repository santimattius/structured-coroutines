plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    // Only Gradle API and Kotlin Gradle Plugin API
    // NO compiler dependencies here
    implementation(gradleApi())
    implementation(kotlin("gradle-plugin-api"))
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

publishing {
    publications {
        // The java-gradle-plugin automatically creates publications
        // We just need to configure the repository
    }
    repositories {
        mavenLocal()
    }
}
