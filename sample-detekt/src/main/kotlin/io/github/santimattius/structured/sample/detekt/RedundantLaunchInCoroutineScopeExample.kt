package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: RedundantLaunchInCoroutineScope (RUNBLOCK_001).
 * Used to validate that :detekt-rules report single launch inside coroutineScope.
 */
suspend fun triggerRedundantLaunchInCoroutineScope() {
    coroutineScope {
        launch {
            delay(1)
        }
    }
}
