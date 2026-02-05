package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Compilation WARNING: redundantLaunchInCoroutineScope
 *
 * coroutineScope with only a single launch is redundant.
 * Execute the work directly or use an explicit external scope.
 */
suspend fun triggerRedundantLaunchInCoroutineScopeWarning() = coroutineScope {
    launch {
        doWork()
    }
}

private suspend fun doWork() {
    // placeholder
}
