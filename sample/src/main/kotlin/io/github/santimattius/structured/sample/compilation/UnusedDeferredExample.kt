package io.github.santimattius.structured.sample.compilation

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Compilation ERROR: unusedDeferred
 *
 * async call creates a Deferred that is never awaited.
 * Call .await() or use launch() if no result is needed.
 */
fun triggerUnusedDeferredError(@StructuredScope scope: CoroutineScope) {
    val deferred = scope.async {
        "result"
    }
    // deferred is never awaited - error reported here
}
