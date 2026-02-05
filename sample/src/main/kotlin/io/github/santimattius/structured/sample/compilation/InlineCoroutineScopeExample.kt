package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Compilation ERROR: inlineCoroutineScope
 *
 * Inline CoroutineScope creation is not allowed. Use @StructuredScope,
 * framework scopes, or coroutineScope { } / supervisorScope { } instead.
 */
fun triggerInlineCoroutineScopeError() {
    CoroutineScope(Dispatchers.IO).launch {
        println("Bad: inline scope")
    }
}
