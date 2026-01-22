plugins {
    kotlin("jvm") version "2.3.0"
    `java-library`
    `maven-publish`
}

dependencies {
    // Android Lint API - compileOnly to avoid runtime conflicts
    compileOnly("com.android.tools.lint:lint-api:31.4.0")
    compileOnly("com.android.tools.lint:lint-checks:31.4.0")
    
    // Testing
    testImplementation("com.android.tools.lint:lint:31.4.0")
    testImplementation("com.android.tools.lint:lint-tests:31.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    useJUnitPlatform()
}

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
            groupId = "io.github.santimattius"
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
}
