package io.github.santimattius.structured.sample.detekt

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Demonstrates INTEROP_001: suspendCoroutine without cancellation support.
 * Used to validate that :detekt-rules report suspendCoroutine usage in suspend functions.
 *
 * BAD: suspendCoroutine does not propagate cancellation from the parent coroutine.
 * When the parent cancels, the callback remains registered, leaking memory and resuming
 * a dead continuation.
 */

// BAD: suspendCoroutine does not support cancellation
suspend fun badFetchData(): String = suspendCoroutine { cont ->
    // Simulated async callback — if parent coroutine cancels, callback is never unregistered
    Thread { cont.resume("data") }.start()
}

// GOOD: suspendCancellableCoroutine with cleanup
suspend fun goodFetchData(): String = suspendCancellableCoroutine { cont ->
    val thread = Thread { cont.resume("data") }
    thread.start()
    cont.invokeOnCancellation { thread.interrupt() }
}
