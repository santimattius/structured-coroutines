package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: CancellationExceptionSwallowed (CANCEL_003).
 * Used to validate that :detekt-rules report catch(Exception) that may swallow CancellationException.
 */
fun triggerCancellationExceptionSwallowed(scope: CoroutineScope) {
    scope.launch {
        try {
            val message = getMessage()
            println(message)
        } catch (ex: Exception) {
            println("Error: ${ex.message}")
        }
    }
}

private suspend fun getMessage(): String {
    delay(1)
    return "ok"
}
