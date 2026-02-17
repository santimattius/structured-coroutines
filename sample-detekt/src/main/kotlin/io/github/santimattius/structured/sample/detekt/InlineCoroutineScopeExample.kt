package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: InlineCoroutineScope (SCOPE_003).
 * Used to validate that :detekt-rules report inline CoroutineScope creation.
 */
fun triggerInlineCoroutineScope() {
    CoroutineScope(Dispatchers.Default).launch {
        println("Orphan from inline scope")
    }
}
