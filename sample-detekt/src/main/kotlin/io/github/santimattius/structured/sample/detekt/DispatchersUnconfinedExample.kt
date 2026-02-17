package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: DispatchersUnconfined.
 * Used to validate that :detekt-rules report Dispatchers.Unconfined usage.
 */
fun triggerDispatchersUnconfined(scope: CoroutineScope) {
    scope.launch(Dispatchers.Unconfined) {
        println("Unconfined")
    }
}
