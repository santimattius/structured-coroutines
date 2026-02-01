plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin {
    // JVM target
    jvm()
    
    // JavaScript targets
    js(IR) {
        browser()
        nodejs()
    }
    
    // Native targets
    // macOS
    macosX64()
    macosArm64()
    
    // iOS
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    // watchOS
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()
    
    // tvOS
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
    
    // Linux
    linuxX64()
    linuxArm64()
    
    // Windows
    mingwX64()
    
    // WASM
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }
    
    // JVM toolchain
    jvmToolchain(17)
    
    // Source sets
    sourceSets {
        commonMain {
            // No dependencies - annotations are self-contained
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-annotations",
        version = project.version.toString()
    )
}