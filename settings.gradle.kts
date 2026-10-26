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
    ":runtime-tracing-jvm",
    ":sample",
    ":sample-detekt"
)
