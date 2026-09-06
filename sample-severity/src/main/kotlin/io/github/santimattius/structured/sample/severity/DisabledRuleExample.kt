package io.github.santimattius.structured.sample.severity

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Scenario: Disabled-suppression.
 *
 * Rule: `globalScopeUsage` (marker `[SCOPE_001]`), default severity `"error"`.
 * Configured: `structuredCoroutines { globalScopeUsage.set("disabled") }` in `build.gradle.kts`.
 * Direction: relaxation (suppression) — always applies immediately, regardless of
 * `severityEnforcement`.
 *
 * With the rule disabled, `./gradlew -p sample-severity compileKotlin` succeeds and
 * `[SCOPE_001]` is entirely absent from the output. See the README's negative control: comment
 * out the `.set("disabled")` line and re-run to see `[SCOPE_001]` reported at `error` again.
 */
fun triggerGlobalScopeUsage() {
    GlobalScope.launch { println("x") }
}
