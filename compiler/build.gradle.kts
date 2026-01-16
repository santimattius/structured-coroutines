plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    // Kotlin Compiler - compileOnly because it's provided at compile time
    compileOnly(kotlin("compiler-embeddable"))
    
    // Annotations module - needed for ClassId references
    implementation(project(":annotations"))
}

kotlin {
    jvmToolchain(17)
    
    compilerOptions {
        // Enable context parameters for FIR checker API
        freeCompilerArgs.add("-Xcontext-parameters")
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
            version = "0.1.0"
            
            from(components["java"])
        }
    }
}
