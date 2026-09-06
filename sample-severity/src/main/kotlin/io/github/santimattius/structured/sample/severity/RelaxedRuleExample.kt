package io.github.santimattius.structured.sample.severity

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Scenario: Immediate-relaxation.
 *
 * Rule: `unusedDeferred` (marker `[SCOPE_002]`), default severity `"error"`.
 * Configured: `structuredCoroutines { unusedDeferred.set("warning") }` in `build.gradle.kts`.
 * Direction: relaxation — applies immediately, regardless of `severityEnforcement`. No
 * grace-period advisory is ever logged for a relaxation.
 *
 * `./gradlew -p sample-severity compileKotlin` succeeds and reports a `w:`-prefixed line
 * containing `[SCOPE_002]`.
 */
fun triggerUnusedDeferred(@StructuredScope scope: CoroutineScope) {
    val deferred = scope.async { 42 }
}
