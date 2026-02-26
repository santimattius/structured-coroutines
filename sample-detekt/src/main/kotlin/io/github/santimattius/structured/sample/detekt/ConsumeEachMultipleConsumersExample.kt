package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.consumeEach
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: ConsumeEachMultipleConsumers (CHANNEL_002).
 * Used to validate that :detekt-rules report same channel with consumeEach from multiple coroutines.
 */
fun triggerConsumeEachMultipleConsumers(scope: CoroutineScope) {
    val ch = Channel<Int>()
    scope.launch { ch.consumeEach { } }
    scope.launch { ch.consumeEach { } }
}
