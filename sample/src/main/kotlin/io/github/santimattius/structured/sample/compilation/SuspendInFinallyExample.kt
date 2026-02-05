package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.delay

/**
 * Compilation WARNING: suspendInFinally
 *
 * Suspend call in finally without NonCancellable context.
 * Wrap critical cleanup in withContext(NonCancellable) { }.
 */
suspend fun triggerSuspendInFinallyWarning() {
    try {
        delay(10)
    } finally {
        delay(1) // suspend in finally without NonCancellable
    }
}
