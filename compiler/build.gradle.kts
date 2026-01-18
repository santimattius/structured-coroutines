plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    // Kotlin Compiler - compileOnly because it's provided at compile time
    compileOnly(kotlin("compiler-embeddable"))
    
    // Test dependencies - using Gradle TestKit for functional testing
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

kotlin {
    jvmToolchain(17)
    
    compilerOptions {
        // Enable context parameters for FIR checker API
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.test {
    useJUnitPlatform()
    
    // Pass plugin JAR location to tests
    dependsOn(":gradle-plugin:jar", ":compiler:jar", ":annotations:jvmJar")
    
    doFirst {
        systemProperty("plugin.jar", project(":compiler").tasks.jar.get().archiveFile.get().asFile.absolutePath)
        systemProperty("annotations.jar", project(":annotations").tasks.named("jvmJar").get().outputs.files.singleFile.absolutePath)
        systemProperty("gradle-plugin.jar", project(":gradle-plugin").tasks.jar.get().archiveFile.get().asFile.absolutePath)
    }
}

// Configure JAR to include the service file
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Structured Coroutines Compiler Plugin",
            "Implementation-Version" to project.version
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.santimattius"
            artifactId = "structured-coroutines-compiler"
            version = project.version.toString()
            
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
