package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.ensureActive

/**
 * Compilation WARNING: loopWithoutYield (4.1 — CANCEL_001)
 *
 * Loops in suspend functions without cooperation points (yield, ensureActive, delay)
 * cannot be cancelled until the loop completes.
 */
suspend fun triggerLoopWithoutYieldWarning() {
    var i = 0
    while (i < 100) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        heavyComputation(i)
        i++
    }
}

@Suppress("UNUSED_PARAMETER")
private fun heavyComputation(n: Int) {
    // Simulated CPU work
}
