package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: ScopeReuseAfterCancel (CANCEL_005).
 * Used to validate that :detekt-rules report scope.cancel() followed by scope.launch.
 */
fun triggerScopeReuseAfterCancel(scope: CoroutineScope) {
    scope.cancel()
    scope.launch { println("reuse") }
}
