package io.github.santimattius.structured.sample.compilation

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Compilation WARNING: dispatchersUnconfined
 *
 * Dispatchers.Unconfined should be avoided in production.
 * Use Dispatchers.Default, Dispatchers.IO, or Dispatchers.Main instead.
 */
fun triggerDispatchersUnconfinedWarning(@StructuredScope scope: CoroutineScope) {
    scope.launch(Dispatchers.Unconfined) {
        println("Warning: Unconfined dispatcher")
    }
}
