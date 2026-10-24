plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish.vanniktech)
}

// :runtime versions independently on its own 0.x line, decoupled from the toolkit's
// PROJECT_VERSION. allprojects {} in the root build.gradle.kts runs before this script,
// so a plain assignment here cleanly wins with no ordering hack.
version = project.findProperty("RUNTIME_VERSION")?.toString() ?: "0.1.0"

kotlin {
    explicitApi()

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
            dependencies {
                // :runtime is the toolkit's first module with a transitive coroutines
                // dependency: api (not implementation) so consumers get it for free.
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-runtime",
        version = project.version.toString()
    )
}
