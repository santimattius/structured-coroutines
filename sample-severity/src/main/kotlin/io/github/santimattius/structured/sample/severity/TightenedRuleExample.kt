package io.github.santimattius.structured.sample.severity

/**
 * Scenario: Deferred-tightening (GRACE, default) / Immediate-tightening (`strict`).
 *
 * Rule: `loopWithoutYield` (marker `[CANCEL_001]`), default severity `"warning"`.
 * Configured: `structuredCoroutines { loopWithoutYield.set("error") }` in `build.gradle.kts`.
 * Direction: tightening — the only line in this sample sensitive to `severityEnforcement`.
 *
 * - GRACE (default, no `-PseverityEnforcement` property): `./gradlew -p sample-severity
 *   compileKotlin` succeeds; `[CANCEL_001]` still reports as a `w:`-prefixed warning, and the
 *   build additionally logs a grace-period advisory naming the rule, the configured `"error"`
 *   value, the current `"warning"` behavior, and the future enforcing release.
 * - STRICT (`./gradlew -p sample-severity compileKotlin -PseverityEnforcement=strict`): the
 *   build FAILS, `[CANCEL_001]` reports as an `e:`-prefixed error, and no advisory is logged.
 */
private fun triggerLoopWithoutYieldWork() {
    println("x")
}

suspend fun triggerLoopWithoutYield() {
    while (true) {
        triggerLoopWithoutYieldWork()
    }
}
