package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Intentionally triggers Detekt rule: UnusedDeferred (SCOPE_002).
 * Used to validate that :detekt-rules report async() result never awaited.
 */
fun triggerUnusedDeferred(scope: CoroutineScope) {
    val d = scope.async { 1 }
}
