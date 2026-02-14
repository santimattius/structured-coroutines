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

// Sources JAR for Maven Central (required for main artifacts)
val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    dependsOn(tasks.named("classes"))
}

// Empty Javadoc JAR (Maven Central expects -javadoc.jar; lint rules typically ship empty)
val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    // Empty jar; Maven Central accepts this for lint/processor libraries
}

fun Project.pomProperty(name: String): String? =
    findProperty(name)?.toString()?.takeIf { it.isNotBlank() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "structured-coroutines-lint-rules"
            version = project.version.toString()

            artifact(lintJar)
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("Structured Coroutines Lint Rules")
                description.set("Android Lint rules for enforcing structured concurrency best practices in Kotlin Coroutines")
                url.set(project.pomProperty("POM_URL") ?: "https://github.com/santimattius/structured-coroutines")
                licenses {
                    license {
                        name.set(project.pomProperty("POM_LICENSE_NAME") ?: "Apache-2.0")
                        url.set(project.pomProperty("POM_LICENSE_URL") ?: "https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set(project.pomProperty("POM_LICENSE_DIST") ?: "repo")
                    }
                }
                developers {
                    developer {
                        id.set(project.pomProperty("POM_DEVELOPER_ID") ?: "santimattius")
                        name.set(project.pomProperty("POM_DEVELOPER_NAME") ?: "Santiago Mattiauda")
                        url.set(project.pomProperty("POM_DEVELOPER_URL") ?: "https://github.com/santimattius")
                    }
                }
                scm {
                    url.set(project.pomProperty("POM_SCM_URL") ?: "https://github.com/santimattius/structured-coroutines")
                    connection.set(project.pomProperty("POM_SCM_CONNECTION") ?: "scm:git:git://github.com/santimattius/structured-coroutines.git")
                    developerConnection.set(project.pomProperty("POM_SCM_DEV_CONNECTION") ?: "scm:git:ssh://git@github.com/santimattius/structured-coroutines.git")
                }
            }
        }
    }
    repositories {
        val username = project.findProperty("mavenCentralUsername")?.toString()
        val password = project.findProperty("mavenCentralPassword")?.toString()
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            maven {
                name = "mavenCentral"
                // Central Portal OSSRH Staging API (s01.oss.sonatype.org was shut down 2025-06-30)
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                credentials {
                    this.username = username
                    this.password = password
                }
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
