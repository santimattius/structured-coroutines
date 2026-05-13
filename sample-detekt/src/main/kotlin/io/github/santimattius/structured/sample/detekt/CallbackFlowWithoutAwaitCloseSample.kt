package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Demonstrates INTEROP_002: callbackFlow without awaitClose.
 * Used to validate that :detekt-rules report callbackFlow missing awaitClose.
 *
 * BAD: callbackFlow without awaitClose causes IllegalStateException at runtime
 * (kotlinx-coroutines >= 1.6) and leaks registered listeners.
 */

interface LocationManager {
    fun register(cb: (String) -> Unit)
    fun unregister(cb: (String) -> Unit)
}

val locationManager = object : LocationManager {
    override fun register(cb: (String) -> Unit) {}
    override fun unregister(cb: (String) -> Unit) {}
}

// BAD: no awaitClose — IllegalStateException at runtime + listener leak
fun badLocationFlow(): Flow<String> = callbackFlow {
    val cb: (String) -> Unit = { trySend(it) }
    locationManager.register(cb)
    // Missing awaitClose!
}

// GOOD: awaitClose ensures listener cleanup
fun goodLocationFlow(): Flow<String> = callbackFlow {
    val cb: (String) -> Unit = { trySend(it) }
    locationManager.register(cb)
    awaitClose { locationManager.unregister(cb) }
}
