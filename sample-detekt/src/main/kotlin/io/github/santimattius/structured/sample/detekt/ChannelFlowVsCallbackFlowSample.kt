package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow

/** Demonstrates INTEROP_003: wrong flow builder for the use case. */

// BAD: channelFlow wrapping external callback without awaitClose
fun badLocationFlow() = channelFlow<Unit> {
    registerListener { trySend(Unit) }
}

// BAD: callbackFlow with only internal work (should be channelFlow)
fun badInternalFlow() = callbackFlow {
    trySend(1)
    awaitClose { }
}

@Suppress("UNUSED_PARAMETER")
private fun registerListener(block: () -> Unit) {}
