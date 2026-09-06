plugins {
    kotlin("jvm")
    id("io.github.santimattius.structured-coroutines")
}

val structuredCoroutinesVersion: String by project
val coroutinesVersion: String by project

dependencies {
    implementation("io.github.santimattius:structured-coroutines-annotations:$structuredCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
}

kotlin {
    jvmToolchain(17)
}

// Demonstrates the structuredCoroutines { } severity DSL (see sdd severity-enforcement,
// closes #68): two relaxations (suppression / immediate relaxation) that always apply
// immediately, and one tightening (loopWithoutYield) whose behavior depends on the
// severityEnforcement policy — GRACE (default) defers it with an advisory, STRICT enforces
// it immediately. Toggle with: ./gradlew -p sample-severity compileKotlin -PseverityEnforcement=strict
structuredCoroutines {
    // Suppression: disables GLOBAL_SCOPE_USAGE (default severity "error") entirely.
    // Relaxation, so it applies immediately regardless of severityEnforcement.
    globalScopeUsage.set("disabled")

    // Immediate relaxation: UNUSED_DEFERRED (default severity "error") relaxed to "warning".
    // Relaxations always apply immediately, regardless of severityEnforcement.
    unusedDeferred.set("warning")

    // Tightening: LOOP_WITHOUT_YIELD (default severity "warning") tightened to "error".
    // This is the only line sensitive to severityEnforcement below.
    loopWithoutYield.set("error")

    // "grace" (default) defers the tightening above by one release with an advisory.
    // "strict" (via -PseverityEnforcement=strict) enforces it immediately.
    severityEnforcement.set(
        providers.gradleProperty("severityEnforcement").orElse("grace")
    )
}
