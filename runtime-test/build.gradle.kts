plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish.vanniktech)
}

// :runtime-test is a separate artifact so production consumers of :runtime never get
// kotlinx-coroutines-test transitively. It versions on the same line as :runtime
// (RUNTIME_VERSION); allprojects {} in the root build.gradle.kts runs before this script.
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
                // api: test code written against this module needs both :runtime types and the
                // kotlinx-coroutines-test API (TestScope, TestCoroutineScheduler, ...).
                api(project(":runtime"))
                api(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test) // TraceSinkContract assertions
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-runtime-test",
        version = project.version.toString()
    )
}
