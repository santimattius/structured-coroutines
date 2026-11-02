plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish.vanniktech)
}

// :runtime-tracing is a separate artifact so :runtime consumers never get androidx.tracing
// transitively. It versions on the same line as :runtime (RUNTIME_VERSION); allprojects {} in the
// root build.gradle.kts runs before this script.
version = project.findProperty("RUNTIME_VERSION")?.toString() ?: "0.1.0"

kotlin {
    explicitApi()

    jvm()

    // Android target (AGP 9 KMP library plugin). Building requires the Android SDK.
    android {
        namespace = "io.github.santimattius.structured.runtime.tracing"
        compileSdk = 36
        minSdk = 23

        // Instrumented tests: run PerfettoTraceSink on a device/emulator with the Android driver.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // Shared by jvm and android: PerfettoTraceSink only needs an androidx.tracing.Tracer.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withCompilations { it.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm }
            }
        }
    }

    jvmToolchain(17)

    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime"))
                // api: PerfettoTraceSink takes an androidx.tracing.Tracer.
                api(libs.androidx.tracing)
            }
        }
        jvmMain {
            dependencies {
                // implementation: only PerfettoTraceSession (the file-backed JVM driver) uses tracing-wire.
                implementation(libs.androidx.tracing.wire)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.tracing.wire)
                implementation(libs.kotlin.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":runtime-test"))
            }
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "structured-coroutines-runtime-tracing",
        version = project.version.toString()
    )
}
