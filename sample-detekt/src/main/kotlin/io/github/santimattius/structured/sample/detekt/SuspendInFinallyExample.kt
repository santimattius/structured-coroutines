package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.delay

/**
 * Intentionally triggers Detekt rule: SuspendInFinally (CANCEL_004).
 * Used to validate that :detekt-rules report suspend in finally without NonCancellable.
 */
suspend fun triggerSuspendInFinally() {
    try {
        delay(1)
    } finally {
        delay(1)
    }
}
