package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: BlockingCallInCoroutine.
 * Used to validate that :detekt-rules report blocking calls inside coroutines.
 */
@Suppress("MagicNumber")
fun triggerBlockingCallInCoroutine(scope: CoroutineScope) {
    scope.launch {
        Thread.sleep(10)
    }
}
