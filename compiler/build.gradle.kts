plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish.vanniktech)
}

dependencies {
    // Kotlin Compiler - compileOnly because it's provided at compile time
    compileOnly(libs.kotlin.compiler.embeddable)

    // Test dependencies - using Gradle TestKit for functional testing
    testImplementation(libs.kotlin.test)
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

    // Publish to Maven Local so functional tests can resolve the plugin via mavenLocal()
    dependsOn(
        ":gradle-plugin:publishToMavenLocal",
        ":compiler:publishToMavenLocal",
        ":annotations:publishToMavenLocal"
    )

    systemProperty("structuredCoroutines.version", project.version.toString())
    systemProperty("structuredCoroutines.rootDir", project.rootProject.projectDir.absolutePath)
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

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-compiler",
        version = project.version.toString()
    )
}

