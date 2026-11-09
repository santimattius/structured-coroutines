pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "structured-coroutines"

include(
    ":annotations",
    ":compiler",
    ":gradle-plugin",
    ":detekt-rules",
    ":lint-rules",
    ":intellij-plugin",
    ":runtime",
    ":runtime-test",
    ":runtime-test-junit4",
    ":runtime-tracing",
    ":runtime-benchmark",
    ":sample",
    ":sample-detekt"
)

// The runtime family lives under coroutines-runtime/ but keeps its flat project paths
// (:runtime, :runtime-test, ...) and Maven coordinates.
project(":runtime").projectDir = file("coroutines-runtime/runtime")
project(":runtime-test").projectDir = file("coroutines-runtime/runtime-test")
project(":runtime-test-junit4").projectDir = file("coroutines-runtime/runtime-test-junit4")
project(":runtime-tracing").projectDir = file("coroutines-runtime/runtime-tracing")
project(":runtime-benchmark").projectDir = file("coroutines-runtime/runtime-benchmark")
