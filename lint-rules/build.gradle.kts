plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    signing
}

dependencies {
    // Android Lint API - compileOnly to avoid runtime conflicts
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)
    
    // Testing
    testImplementation(libs.lint)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
}

// Lint 31.4.0 embeds Kotlin 2.0; align test runtime to avoid "metadata version 2.3.0, expected 2.0.0"
configurations.testRuntimeClasspath.get().resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")

kotlin {
    jvmToolchain(17)
}

// Tests use @org.junit.Test (JUnit 4); lint-tests expects JUnit 4. Do not use useJUnitPlatform().

// Create JAR with Lint rules
val lintJar = tasks.register<Jar>("lintJar") {
    archiveBaseName.set("structured-coroutines-lint-rules")
    archiveClassifier.set("")
    
    // Include compiled classes and resources
    from(sourceSets.main.get().output)
    
    manifest {
        attributes("Lint-Registry-v2" to "io.github.santimattius.structured.lint.StructuredCoroutinesIssueRegistry")
    }
    
    // Ensure this task runs after compilation
    dependsOn(tasks.named("classes"))
    
    // Make sure resources are included
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Make lintJar the default artifact
tasks.named("jar") {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "structured-coroutines-lint-rules"
            version = project.version.toString()

            // Use the lintJar task instead of default jar
            artifact(lintJar)

            pom {
                name.set("Structured Coroutines Lint Rules")
                description.set("Android Lint rules for enforcing structured concurrency best practices in Kotlin Coroutines")
                url.set("https://github.com/santimattius/structured-coroutines")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("santimattius")
                        name.set("Santiago Mattiauda")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "mavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("mavenCentralUsername")?.toString() ?: ""
                password = project.findProperty("mavenCentralPassword")?.toString() ?: ""
            }
        }
    }
}

signing {
    val keyId = project.findProperty("signingInMemoryKeyId")?.toString()
    val key = project.findProperty("signingInMemoryKey")?.toString()
    val password = project.findProperty("signingInMemoryKeyPassword")?.toString()
    if (key != null && key.isNotEmpty()) {
        useInMemoryPgpKeys(keyId, key, password)
    }
    sign(publishing.publications["maven"])
}
