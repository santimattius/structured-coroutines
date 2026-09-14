rootProject.name = "structured-coroutines"

include(
    ":annotations",
    ":compiler",
    ":gradle-plugin",
    ":detekt-rules",
    ":lint-rules",
    ":sample",
    ":sample-detekt"
)

// :intellij-plugin is isolated as a separate included build so it can pin its
// own Kotlin Gradle Plugin version independently of the root buildscript
// classpath. See intellij-plugin/settings.gradle.kts and
// docs/KOTLIN_2_4_READINESS_CHECKLIST.md for the unblock criteria that will
// let it rejoin the root catalog's Kotlin version.
includeBuild("intellij-plugin")
