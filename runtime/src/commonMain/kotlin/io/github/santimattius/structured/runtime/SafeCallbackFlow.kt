package io.github.santimattius.structured.runtime

import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Builds a [Flow] from a callback-based API while making cleanup impossible to forget.
 *
 * [register] is invoked synchronously to wire up the callback source, returning a
 * [DisposableHandle] that is guaranteed to be disposed exactly once when the flow's
 * collector terminates (completes, is cancelled, or the callback source itself closes
 * the channel).
 *
 * The registration callback receives a [SendChannel] rather than the full
 * `ProducerScope` so it cannot launch unmanaged children — it is expected to be a
 * synchronous bridge into a callback API only.
 *
 * If [register] throws before returning a [DisposableHandle], the exception propagates
 * to the collector unchanged and no dispose is attempted, since registration never
 * produced a handle to dispose.
 *
 * Example:
 * ```
 * fun locations(client: LocationClient): Flow<Location> = safeCallbackFlow { out ->
 *     val listener = LocationListener { out.trySend(it) }
 *     client.register(listener)
 *     DisposableHandle { client.unregister(listener) }
 * }
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param register wires the callback source to the provided [SendChannel] and returns the handle
 * that releases it. Called once per collection.
 * @return a cold [Flow] that registers on collection and disposes on termination.
 * @see callbackFlow
 */
@ExperimentalStructuredCoroutines
public fun <T> safeCallbackFlow(
    register: (SendChannel<T>) -> DisposableHandle,
): Flow<T> = callbackFlow {
    val handle = register(this)
    awaitClose { handle.dispose() }
}
