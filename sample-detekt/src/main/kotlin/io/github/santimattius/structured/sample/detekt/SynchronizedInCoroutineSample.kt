package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.*

/** Triggers CONCUR_001 — synchronized inside suspend. */
@Suppress("UNUSED_PARAMETER")
suspend fun synchronizedInSuspend(lock: Any) {
    synchronized(lock) { }
}
