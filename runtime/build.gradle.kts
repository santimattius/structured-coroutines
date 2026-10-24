plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
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

    // Android target (AGP 9 KMP library plugin: single variant, no build types/flavors).
    // Building requires the Android SDK (ANDROID_HOME or sdk.dir in local.properties).
    android {
        namespace = "io.github.santimattius.structured.runtime"
        compileSdk = 36
        minSdk = 23
    }

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

    // Default hierarchy + a custom intermediate source set shared by jvm and android
    // (hosts the IoCompat actual). Manual dependsOn() would disable the default template.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withCompilations { it.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm }
            }
        }
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
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
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
