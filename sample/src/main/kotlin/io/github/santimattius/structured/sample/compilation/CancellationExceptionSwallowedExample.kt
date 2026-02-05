package io.github.santimattius.structured.sample.compilation

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compilation WARNING: cancellationExceptionSwallowed
 *
 * catch(Exception) may swallow CancellationException.
 * Add catch(CancellationException) { throw it } or rethrow in the catch block.
 */
suspend fun triggerCancellationExceptionSwallowedWarning() {
    try {
        riskyWork()
    } catch (e: Exception) {
        // CancellationException could be swallowed
        println(e.message)
    }
}

/**
 * Same WARNING inside a launch block (suspend lambda).
 * Generic exception handled in launch { } can swallow CancellationException.
 */
fun triggerCancellationExceptionSwallowedInLaunchBlock(@StructuredScope scope: CoroutineScope) {
    scope.launch {
        try {
            val message = getRandomMessage()
            println(message)
        } catch (ex: Exception) {
            println("Error: ${ex.message}")
        }
    }
}

private suspend fun riskyWork() {
    delay(1)
}

private suspend fun getRandomMessage(): String = "ok"
