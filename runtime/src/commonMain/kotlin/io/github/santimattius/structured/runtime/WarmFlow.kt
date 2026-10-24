package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private class WarmProgress(val count: Int, val done: Boolean, val error: Throwable?)

/**
 * Warm flow: a hybrid between cold and hot. The upstream does not start until the first
 * collector asks for data, runs exactly once in [scope], and memoizes every emitted value, so
 * later (or concurrent) collectors replay the stored values and then follow the live upstream
 * instead of re-running it.
 *
 * - Completion is memoized: collectors finish when the upstream completes (unlike `shareIn`,
 *   whose flow never completes).
 * - An upstream failure is memoized and rethrown to every collector after the values emitted
 *   before it.
 * - The upstream is never restarted and keeps running even if all collectors leave; it ends
 *   when it completes or when [scope] is cancelled (collectors then receive the cancellation
 *   as a failure instead of hanging).
 * - All emitted values are retained in memory: use it for finite, small flows (e.g. an
 *   expensive RPC) only.
 *
 * Example:
 * ```
 * val items = flow { emit(rpc.load()) }.warmIn(appScope)
 * items.collect { render(it) }
 * items.collect { audit(it) } // no second RPC
 * ```
 *
 * Pattern: warm flows, so that repeated collection of a cold flow cannot unintentionally
 * multiply calls against a network dependency.
 * Not tied to a lint rule: it is a server-side pattern, not a best-practice diagnostic.
 */
@ExperimentalStructuredCoroutines
public fun <T> Flow<T>.warmIn(scope: CoroutineScope): Flow<T> {
    val upstream = this
    val values = ArrayList<T>()
    val lock = Mutex()
    val progress = MutableStateFlow(WarmProgress(0, false, null))
    val job = scope.launch(start = CoroutineStart.LAZY) {
        var failure: Throwable? = null
        try {
            upstream.collect { value ->
                lock.withLock { values.add(value) }
                progress.value = WarmProgress(lock.withLock { values.size }, false, null)
            }
        } catch (t: Throwable) {
            failure = t
        } finally {
            withContext(NonCancellable) {
                progress.value = WarmProgress(lock.withLock { values.size }, true, failure)
            }
        }
    }
    return flow {
        job.start()
        var next = 0
        while (true) {
            val snapshot = progress.first { it.count > next || it.done }
            val batch = lock.withLock { values.subList(next, snapshot.count).toList() }
            batch.forEach { emit(it) }
            next += batch.size
            if (snapshot.done && next >= snapshot.count) {
                snapshot.error?.let { throw it }
                return@flow
            }
        }
    }
}
